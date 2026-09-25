package app.platform.recommendation;

import app.platform.catalog.Catalog;
import app.platform.compatibility.BuildParts;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.compatibility.CompatibilityReport;
import app.platform.compatibility.Fit;
import app.platform.compatibility.PowerEstimate;
import app.platform.compatibility.PowerEstimator;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.Gpu;
import app.platform.hardware.HardwareComponent;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.Storage;
import app.platform.pricing.Offer;
import app.platform.pricing.PriceService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Chooses parts for a request.
 *
 * <p>For every eligible CPU × GPU pair the engine completes the build with the cheapest compatible
 * motherboard, memory, drive, cooler, case and power supply, then keeps the pair with the best
 * weighted performance that fits the budget. Every pairing decision goes through {@link Fit}, the
 * same checks the compatibility engine explains, and the final build is verified by
 * {@link CompatibilityEngine}.
 */
public final class RecommendationEngine {

    public static final String VERSION = "rec-v1";

    /** Coolers must be at least this tall for CPUs above the matching power level (heuristic v1). */
    private static final int HIGH_POWER_WATTS = 150;
    private static final int HIGH_POWER_COOLER_HEIGHT_MM = 150;
    private static final int MID_POWER_WATTS = 100;
    private static final int MID_POWER_COOLER_HEIGHT_MM = 125;
    /** Bundled coolers are used only for CPUs whose maximum power stays at or below this (heuristic v1). */
    private static final int STOCK_COOLER_MAX_WATTS = 125;
    /** Tier gap between GPU and gaming CPU tolerated before a pairing is considered unbalanced (heuristic v1). */
    private static final double IMBALANCE_TOLERANCE = 0.25;

    private final PriceService prices;
    private final CompatibilityEngine compatibility;
    private volatile Pools cachedPools;

    public RecommendationEngine(PriceService prices, CompatibilityEngine compatibility) {
        this.prices = prices;
        this.compatibility = compatibility;
    }

    // ---------------------------------------------------------------------------------------------
    // Recommendation
    // ---------------------------------------------------------------------------------------------

    public Recommendation recommend(Catalog catalog, BuildRequest request) {
        RequirementProfile profile = RequirementAnalyzer.analyze(request);
        Pools pools = pools(catalog);
        BuildParts owned = resolveOwned(catalog, request.ownedComponentIds());
        Set<UUID> ownedIds = new HashSet<>(request.ownedComponentIds());

        Attempt withTargets = search(pools, owned, ownedIds, profile, request.budgetBrl(), profile.targetRamGb(), profile.targetStorageGb());
        Attempt withMinimums = search(pools, owned, ownedIds, profile, request.budgetBrl(), profile.minRamGb(), profile.minStorageGb());

        List<String> notes = new ArrayList<>();
        Candidate chosen;
        boolean withinBudget = true;
        if (withTargets.best() != null
                && (withMinimums.best() == null || withTargets.best().utility() >= 0.9 * withMinimums.best().utility())) {
            chosen = withTargets.best();
        } else if (withMinimums.best() != null) {
            chosen = upgradeWithLeftover(pools, withMinimums.best(), ownedIds, profile, request.budgetBrl());
        } else if (withMinimums.cheapest() != null) {
            chosen = withMinimums.cheapest();
            withinBudget = false;
            notes.add("Com " + brl(request.budgetBrl()) + " não encontramos uma configuração completa e compatível para esse uso. "
                    + "A opção mais em conta que encontramos custa " + brl(chosen.cost()) + ".");
        } else {
            throw new NoFeasibleBuildException(
                    "Não encontramos uma combinação compatível com as peças informadas. Revise as peças que você já tem.");
        }

        CompatibilityReport report = compatibility.check(chosen.parts());
        if (!report.isBuildable()) {
            // Selection only uses parts that pass Fit; reaching this means owned parts conflict.
            notes.add("As peças que você já tem têm um problema de compatibilidade — veja os detalhes abaixo.");
        }
        notes.addAll(explainDecisions(chosen, request, profile, ownedIds));
        return new Recommendation(chosen.parts(), ownedIds, profile, chosen.cost(), withinBudget, notes);
    }

    private Attempt search(Pools pools, BuildParts owned, Set<UUID> ownedIds, RequirementProfile profile,
                           BigDecimal budget, int ramGb, int storageGb) {
        List<Priced<Cpu>> cpuOptions = cpuOptions(pools, owned, profile);
        List<Priced<Gpu>> gpuOptions = gpuOptions(pools, owned, profile);

        Candidate best = null;
        Candidate cheapest = null;
        for (Priced<Cpu> cpu : cpuOptions) {
            Platform platform = platform(pools, owned, cpu.part(), ramGb, storageGb);
            if (platform == null) {
                continue;
            }
            for (Priced<Gpu> gpu : gpuOptions) {
                Gpu gpuPart = gpu == null ? null : gpu.part();
                if (gpuPart == null && !Boolean.TRUE.equals(cpu.part().integratedGraphics())) {
                    continue;
                }
                Chassis chassis = chassis(pools, owned, cpu.part(), gpuPart, platform);
                if (chassis == null) {
                    continue;
                }
                BigDecimal cost = cpu.price().add(gpu == null ? BigDecimal.ZERO : gpu.price())
                        .add(platform.cost()).add(chassis.cost());
                BuildParts parts = new BuildParts(cpu.part(), gpuPart, platform.board().part(), platform.memory().part(),
                        platform.storage().stream().map(Priced::part).toList(), chassis.psu().part(), chassis.pcCase().part(),
                        platform.cooler() == null ? null : platform.cooler().part());
                Candidate candidate = new Candidate(parts, cost, utility(pools, profile, cpu.part(), gpuPart));
                if (cheapest == null || cost.compareTo(cheapest.cost()) < 0) {
                    cheapest = candidate;
                }
                if (cost.compareTo(budget) <= 0 && (best == null || candidate.betterThan(best))) {
                    best = candidate;
                }
            }
        }
        return new Attempt(best, cheapest);
    }

    private List<Priced<Cpu>> cpuOptions(Pools pools, BuildParts owned, RequirementProfile profile) {
        if (owned.cpu() != null) {
            return List.of(new Priced<>(owned.cpu(), BigDecimal.ZERO));
        }
        List<Priced<Cpu>> options = pools.cpus();
        if (owned.motherboard() != null) {
            Motherboard board = owned.motherboard();
            options = options.stream()
                    .filter(cpu -> Fit.socket(cpu.part(), board) == Fit.Verdict.YES
                            && Fit.generationSupport(cpu.part(), board) == Fit.Verdict.YES
                            && Fit.cpuSupportsBoardMemory(cpu.part(), board) != Fit.Verdict.NO)
                    .toList();
        }
        List<Priced<Cpu>> meetingThreads = options.stream()
                .filter(cpu -> cpu.part().threads() >= profile.minCpuThreads())
                .toList();
        return meetingThreads.isEmpty() ? options : meetingThreads;
    }

    private List<Priced<Gpu>> gpuOptions(Pools pools, BuildParts owned, RequirementProfile profile) {
        if (owned.gpu() != null) {
            return List.of(new Priced<>(owned.gpu(), BigDecimal.ZERO));
        }
        List<Priced<Gpu>> options = new ArrayList<>();
        if (profile.needsDedicatedGpu()) {
            List<Priced<Gpu>> enoughVram = pools.gpus().stream()
                    .filter(gpu -> gpu.part().vramGb() >= profile.minVramGb())
                    .toList();
            options.addAll(enoughVram.isEmpty() ? pools.gpus() : enoughVram);
        } else {
            options.add(null);
            pools.gpus().stream().sorted(Comparator.comparing(Priced::price)).limit(4).forEach(options::add);
        }
        return options;
    }

    private Platform platform(Pools pools, BuildParts owned, Cpu cpu, int ramGb, int storageGb) {
        List<Priced<Motherboard>> boards = owned.motherboard() != null
                ? List.of(new Priced<>(owned.motherboard(), BigDecimal.ZERO))
                : pools.boards().stream()
                        .filter(board -> Fit.socket(cpu, board.part()) == Fit.Verdict.YES
                                && Fit.generationSupport(cpu, board.part()) == Fit.Verdict.YES
                                && Fit.cpuSupportsBoardMemory(cpu, board.part()) == Fit.Verdict.YES)
                        .toList();

        Priced<Motherboard> bestBoard = null;
        Priced<Memory> bestMemory = null;
        for (String ramType : List.of("DDR4", "DDR5")) {
            Priced<Motherboard> board = boards.stream()
                    .filter(candidate -> ramType.equals(candidate.part().ramType()))
                    .findFirst().orElse(null);
            if (board == null) {
                continue;
            }
            Priced<Memory> memory = owned.memory() != null
                    ? (ramType.equals(owned.memory().ramType()) ? new Priced<>(owned.memory(), BigDecimal.ZERO) : null)
                    : cheapestMemory(pools, board.part(), ramGb);
            if (memory == null) {
                continue;
            }
            if (bestBoard == null || board.price().add(memory.price()).compareTo(bestBoard.price().add(bestMemory.price())) < 0) {
                bestBoard = board;
                bestMemory = memory;
            }
        }
        if (bestBoard == null) {
            return null;
        }

        List<Priced<Storage>> storage;
        if (!owned.storage().isEmpty()) {
            storage = owned.storage().stream().map(drive -> new Priced<>(drive, BigDecimal.ZERO)).toList();
        } else {
            Priced<Storage> drive = cheapestDrive(pools, bestBoard.part(), storageGb);
            if (drive == null) {
                return null;
            }
            storage = List.of(drive);
        }

        Priced<CpuCooler> cooler = null;
        if (owned.cooler() != null) {
            cooler = new Priced<>(owned.cooler(), BigDecimal.ZERO);
        } else if (!stockCoolerIsEnough(cpu)) {
            int minHeight = minimumCoolerHeight(cpu);
            cooler = pools.coolers().stream()
                    .filter(candidate -> Fit.coolerSocket(candidate.part(), cpu) == Fit.Verdict.YES
                            && candidate.part().heightMm() >= minHeight)
                    .findFirst().orElse(null);
            if (cooler == null) {
                return null;
            }
        }

        BigDecimal cost = bestBoard.price().add(bestMemory.price())
                .add(storage.stream().map(Priced::price).reduce(BigDecimal.ZERO, BigDecimal::add))
                .add(cooler == null ? BigDecimal.ZERO : cooler.price());
        return new Platform(bestBoard, bestMemory, storage, cooler, cost);
    }

    private Chassis chassis(Pools pools, BuildParts owned, Cpu cpu, Gpu gpu, Platform platform) {
        Motherboard board = platform.board().part();
        CpuCooler cooler = platform.cooler() == null ? null : platform.cooler().part();

        Priced<PcCase> pcCase = owned.pcCase() != null
                ? new Priced<>(owned.pcCase(), BigDecimal.ZERO)
                : pools.cases().stream()
                        .filter(candidate -> caseFits(candidate.part(), board, gpu, cooler))
                        .findFirst().orElse(null);
        if (pcCase == null) {
            return null;
        }

        PowerEstimate power = PowerEstimator.estimate(cpu, gpu, platform.storage().size());
        Priced<PowerSupply> psu = owned.powerSupply() != null
                ? new Priced<>(owned.powerSupply(), BigDecimal.ZERO)
                : pools.psus().stream()
                        .filter(candidate -> psuFits(candidate.part(), power, gpu, pcCase.part()))
                        .findFirst().orElse(null);
        if (psu == null) {
            return null;
        }
        return new Chassis(pcCase, psu, pcCase.price().add(psu.price()));
    }

    static boolean caseFits(PcCase pcCase, Motherboard board, Gpu gpu, CpuCooler cooler) {
        if (Fit.motherboardInCase(board, pcCase) != Fit.Verdict.YES) {
            return false;
        }
        if (gpu != null && (Fit.gpuLengthInCase(gpu, pcCase) != Fit.Verdict.YES
                || pcCase.maxGpuLengthMm() - gpu.lengthMm() < Fit.TIGHT_CLEARANCE_MM)) {
            return false;
        }
        return cooler == null || Fit.coolerHeightInCase(cooler, pcCase) == Fit.Verdict.YES
                && pcCase.maxCoolerHeightMm() - cooler.heightMm() >= Fit.TIGHT_CLEARANCE_MM;
    }

    static boolean psuFits(PowerSupply psu, PowerEstimate power, Gpu gpu, PcCase pcCase) {
        return psu.wattage() >= power.recommendedPsuWatts()
                && (gpu == null || Fit.psuConnectorsForGpu(psu, gpu) == Fit.Verdict.YES)
                && Fit.psuFormFactorInCase(psu, pcCase) == Fit.Verdict.YES
                && Fit.psuLengthInCase(psu, pcCase) != Fit.Verdict.NO;
    }

    static Priced<Memory> cheapestMemory(Pools pools, Motherboard board, int capacityGb) {
        Predicate<Priced<Memory>> fits = kit -> kit.part().ramType().equals(board.ramType())
                && kit.part().totalCapacityGb() >= capacityGb
                && Fit.memorySlots(kit.part(), board) == Fit.Verdict.YES
                && Fit.memoryCapacity(kit.part(), board) == Fit.Verdict.YES;
        // Two identical modules run in dual channel; prefer them over a single module.
        return pools.memory().stream().filter(fits.and(kit -> kit.part().modules() == 2)).findFirst()
                .or(() -> pools.memory().stream().filter(fits).findFirst())
                .orElse(null);
    }

    static Priced<Storage> cheapestDrive(Pools pools, Motherboard board, int capacityGb) {
        return pools.drives().stream()
                .filter(drive -> drive.part().capacityGb() >= capacityGb
                        && Fit.m2DriveOnBoard(drive.part(), board) == Fit.Verdict.YES)
                .findFirst().orElse(null);
    }

    static boolean stockCoolerIsEnough(Cpu cpu) {
        return Boolean.TRUE.equals(cpu.includesCooler()) && cpu.powerBudgetWatts() != null && cpu.powerBudgetWatts() <= STOCK_COOLER_MAX_WATTS;
    }

    static int minimumCoolerHeight(Cpu cpu) {
        int power = Objects.requireNonNullElse(cpu.powerBudgetWatts(), HIGH_POWER_WATTS);
        if (power >= HIGH_POWER_WATTS) {
            return HIGH_POWER_COOLER_HEIGHT_MM;
        }
        return power >= MID_POWER_WATTS ? MID_POWER_COOLER_HEIGHT_MM : 0;
    }

    /**
     * Weighted performance. Each score is placed on a 0–1 log scale between the weakest and strongest eligible
     * part, so GPUs (a ~30× range) and CPUs (a ~5× range) are comparable tier for tier. For gaming, a GPU far
     * stronger or far weaker than the CPU is penalized: the weaker part would hold the other back.
     */
    static double utility(Pools pools, RequirementProfile profile, Cpu cpu, Gpu gpu) {
        double enough = profile.enoughPerformance();
        double gpuNorm = gpu == null ? 0 : Math.min(enough, pools.gpuRange().normalize(PerformanceEstimator.gpuScore(gpu)));
        double multiNorm = Math.min(enough, pools.cpuMultiRange().normalize(PerformanceEstimator.cpuMultiThreadScore(cpu)));
        double gamingNorm = Math.min(enough, pools.cpuGamingRange().normalize(PerformanceEstimator.cpuGamingScore(cpu)));
        double utility = profile.gpuWeight() * gpuNorm
                + profile.cpuMultiThreadWeight() * multiNorm
                + profile.cpuGamingWeight() * gamingNorm;
        if (profile.gamingFocused() && gpu != null) {
            utility -= profile.gpuWeight() * Math.max(0, Math.abs(gpuNorm - gamingNorm) - IMBALANCE_TOLERANCE);
        }
        return utility;
    }

    /** When the budget only fit minimum memory/storage, spend what is left to reach the targets. */
    private Candidate upgradeWithLeftover(Pools pools, Candidate candidate, Set<UUID> ownedIds, RequirementProfile profile, BigDecimal budget) {
        BuildParts parts = candidate.parts();
        BigDecimal cost = candidate.cost();

        Memory memory = parts.memory();
        if (!ownedIds.contains(memory.id()) && memory.totalCapacityGb() < profile.targetRamGb()) {
            Priced<Memory> bigger = cheapestMemory(pools, parts.motherboard(), profile.targetRamGb());
            if (bigger != null) {
                BigDecimal newCost = cost.subtract(pools.price(memory)).add(bigger.price());
                if (newCost.compareTo(budget) <= 0) {
                    parts = parts.withMemory(bigger.part());
                    cost = newCost;
                }
            }
        }
        Storage drive = parts.storage().getFirst();
        if (!ownedIds.contains(drive.id()) && drive.capacityGb() < profile.targetStorageGb()) {
            Priced<Storage> bigger = cheapestDrive(pools, parts.motherboard(), profile.targetStorageGb());
            if (bigger != null) {
                BigDecimal newCost = cost.subtract(pools.price(drive)).add(bigger.price());
                if (newCost.compareTo(budget) <= 0) {
                    parts = parts.withStorage(List.of(bigger.part()));
                    cost = newCost;
                }
            }
        }
        return new Candidate(parts, cost, candidate.utility());
    }

    private static List<String> explainDecisions(Candidate chosen, BuildRequest request, RequirementProfile profile, Set<UUID> ownedIds) {
        List<String> notes = new ArrayList<>();
        BuildParts parts = chosen.parts();
        if (parts.gpu() == null) {
            notes.add("Não incluímos placa de vídeo: o vídeo integrado do processador atende ao que você vai fazer. Dá para adicionar uma depois.");
        }
        if (parts.cooler() == null && parts.cpu() != null && Boolean.TRUE.equals(parts.cpu().includesCooler())) {
            notes.add("O processador já vem com cooler na caixa, então não incluímos um separado.");
        }
        if (!ownedIds.isEmpty()) {
            String reused = parts.all().stream()
                    .filter(part -> ownedIds.contains(part.id()))
                    .map(part -> part.category().label().toLowerCase(Locale.ROOT))
                    .distinct()
                    .collect(Collectors.joining(", "));
            notes.add("Aproveitamos as peças que você já tem (" + reused + "). Elas não entram no total.");
        }
        BigDecimal leftover = request.budgetBrl().subtract(chosen.cost());
        if (leftover.compareTo(request.budgetBrl().multiply(new BigDecimal("0.12"))) > 0) {
            notes.add("Sobraram " + brl(leftover) + " do orçamento. Gastar mais não traria ganho relevante para o uso informado.");
        }
        return notes;
    }

    // ---------------------------------------------------------------------------------------------
    // Alternatives
    // ---------------------------------------------------------------------------------------------

    /** Swaps that keep the rest of the build compatible, for each part the person does not already own. */
    public Map<ComponentCategory, List<Alternative>> alternatives(Catalog catalog, BuildParts parts, Set<UUID> ownedIds, RequirementProfile profile) {
        Map<ComponentCategory, List<Alternative>> result = new EnumMap<>(ComponentCategory.class);
        boolean complete = parts.cpu() != null && parts.motherboard() != null && parts.memory() != null
                && parts.storage().size() == 1 && parts.powerSupply() != null && parts.pcCase() != null;
        if (!complete) {
            return result;
        }
        Pools pools = pools(catalog);
        if (parts.gpu() != null && !ownedIds.contains(parts.gpu().id())) {
            result.put(ComponentCategory.GPU, gpuAlternatives(pools, parts));
        }
        if (!ownedIds.contains(parts.cpu().id())) {
            result.put(ComponentCategory.CPU, cpuAlternatives(pools, parts, profile));
        }
        if (!ownedIds.contains(parts.memory().id())) {
            result.put(ComponentCategory.MEMORY, memoryAlternatives(pools, parts));
        }
        if (!ownedIds.contains(parts.storage().getFirst().id())) {
            result.put(ComponentCategory.STORAGE, storageAlternatives(pools, parts));
        }
        if (!ownedIds.contains(parts.motherboard().id())) {
            result.put(ComponentCategory.MOTHERBOARD, boardAlternatives(pools, parts));
        }
        if (!ownedIds.contains(parts.powerSupply().id())) {
            result.put(ComponentCategory.POWER_SUPPLY, psuAlternatives(pools, parts));
        }
        result.values().removeIf(List::isEmpty);
        return result;
    }

    private List<Alternative> gpuAlternatives(Pools pools, BuildParts parts) {
        Gpu current = parts.gpu();
        Double currentScore = PerformanceEstimator.gpuScore(current);
        BigDecimal currentPrice = pools.price(current);
        if (currentScore == null || currentPrice == null) {
            return List.of();
        }
        List<Priced<Gpu>> fitting = pools.gpus().stream()
                .filter(gpu -> !gpu.part().chipset().equals(current.chipset()))
                .filter(gpu -> caseFits(parts.pcCase(), parts.motherboard(), gpu.part(), null))
                .filter(gpu -> psuFits(parts.powerSupply(),
                        PowerEstimator.estimate(parts.cpu(), gpu.part(), parts.storage().size()), gpu.part(), parts.pcCase()))
                .toList();
        List<Alternative> alternatives = new ArrayList<>();
        fitting.stream()
                .filter(gpu -> PerformanceEstimator.gpuScore(gpu.part()) < currentScore * 0.97 && gpu.price().compareTo(currentPrice) < 0)
                .max(Comparator.comparingDouble(gpu -> PerformanceEstimator.gpuScore(gpu.part())))
                .ifPresent(gpu -> alternatives.add(alternative(gpu, currentPrice, Alternative.Direction.CHEAPER,
                        percentText(PerformanceEstimator.gpuScore(gpu.part()), currentScore, "desempenho gráfico"))));
        fitting.stream()
                .filter(gpu -> PerformanceEstimator.gpuScore(gpu.part()) > currentScore * 1.05 && gpu.price().compareTo(currentPrice) > 0)
                .min(Comparator.comparing((Priced<Gpu> gpu) -> gpu.price()))
                .ifPresent(gpu -> alternatives.add(alternative(gpu, currentPrice, Alternative.Direction.BETTER,
                        percentText(PerformanceEstimator.gpuScore(gpu.part()), currentScore, "desempenho gráfico"))));
        return alternatives;
    }

    private List<Alternative> cpuAlternatives(Pools pools, BuildParts parts, RequirementProfile profile) {
        Cpu current = parts.cpu();
        BigDecimal currentPrice = pools.price(current);
        if (currentPrice == null) {
            return List.of();
        }
        boolean gaming = profile == null || profile.gamingFocused();
        String metric = gaming ? "desempenho em jogos" : "desempenho com vários programas";
        ToDoubleFunction<Cpu> score = cpu -> gaming
                ? PerformanceEstimator.cpuGamingScore(cpu)
                : PerformanceEstimator.cpuMultiThreadScore(cpu);
        double currentScore = score.applyAsDouble(current);
        PowerSupply psu = parts.powerSupply();
        List<Priced<Cpu>> fitting = pools.cpus().stream()
                .filter(cpu -> !cpu.part().id().equals(current.id()))
                .filter(cpu -> Fit.socket(cpu.part(), parts.motherboard()) == Fit.Verdict.YES
                        && Fit.generationSupport(cpu.part(), parts.motherboard()) == Fit.Verdict.YES
                        && Fit.cpuSupportsBoardMemory(cpu.part(), parts.motherboard()) == Fit.Verdict.YES)
                .filter(cpu -> parts.cooler() == null
                        ? stockCoolerIsEnough(cpu.part())
                        : Fit.coolerSocket(parts.cooler(), cpu.part()) == Fit.Verdict.YES
                                && parts.cooler().heightMm() != null && parts.cooler().heightMm() >= minimumCoolerHeight(cpu.part()))
                .filter(cpu -> parts.gpu() != null || Boolean.TRUE.equals(cpu.part().integratedGraphics()))
                .filter(cpu -> psu.wattage() != null && psu.wattage() >= PowerEstimator.estimate(cpu.part(), parts.gpu(), parts.storage().size()).recommendedPsuWatts())
                .toList();
        List<Alternative> alternatives = new ArrayList<>();
        fitting.stream()
                .filter(cpu -> score.applyAsDouble(cpu.part()) < currentScore * 0.97 && cpu.price().compareTo(currentPrice) < 0)
                .max(Comparator.comparingDouble(cpu -> score.applyAsDouble(cpu.part())))
                .ifPresent(cpu -> alternatives.add(alternative(cpu, currentPrice, Alternative.Direction.CHEAPER,
                        percentText(score.applyAsDouble(cpu.part()), currentScore, metric))));
        fitting.stream()
                .filter(cpu -> score.applyAsDouble(cpu.part()) > currentScore * 1.05 && cpu.price().compareTo(currentPrice) > 0)
                .min(Comparator.comparing((Priced<Cpu> cpu) -> cpu.price()))
                .ifPresent(cpu -> alternatives.add(alternative(cpu, currentPrice, Alternative.Direction.BETTER,
                        percentText(score.applyAsDouble(cpu.part()), currentScore, metric))));
        return alternatives;
    }

    private List<Alternative> memoryAlternatives(Pools pools, BuildParts parts) {
        Memory current = parts.memory();
        BigDecimal currentPrice = pools.price(current);
        if (currentPrice == null || current.totalCapacityGb() == null) {
            return List.of();
        }
        List<Alternative> alternatives = new ArrayList<>();
        int smaller = current.totalCapacityGb() / 2;
        if (smaller >= 8) {
            Priced<Memory> kit = cheapestMemory(pools, parts.motherboard(), smaller);
            if (kit != null && kit.part().totalCapacityGb() == smaller) {
                alternatives.add(alternative(kit, currentPrice, Alternative.Direction.CHEAPER,
                        "Metade da memória (" + smaller + " GB): pode ficar lento com muitos programas ou abas abertos ao mesmo tempo."));
            }
        }
        int bigger = current.totalCapacityGb() * 2;
        Priced<Memory> kit = cheapestMemory(pools, parts.motherboard(), bigger);
        if (kit != null && kit.part().totalCapacityGb() == bigger) {
            alternatives.add(alternative(kit, currentPrice, Alternative.Direction.BETTER,
                    "O dobro de memória (" + bigger + " GB): mais folga para programas pesados, máquinas virtuais e muitas abas."));
        }
        return alternatives;
    }

    private List<Alternative> storageAlternatives(Pools pools, BuildParts parts) {
        Storage current = parts.storage().getFirst();
        BigDecimal currentPrice = pools.price(current);
        if (currentPrice == null || current.capacityGb() == null) {
            return List.of();
        }
        List<Alternative> alternatives = new ArrayList<>();
        int smaller = current.capacityGb() / 2;
        if (smaller >= 480) {
            Priced<Storage> drive = cheapestDrive(pools, parts.motherboard(), smaller);
            if (drive != null && drive.part().capacityGb() < current.capacityGb()) {
                alternatives.add(alternative(drive, currentPrice, Alternative.Direction.CHEAPER,
                        "Menos espaço (" + capacityText(drive.part().capacityGb()) + "): cabem menos jogos e arquivos."));
            }
        }
        Priced<Storage> drive = cheapestDrive(pools, parts.motherboard(), current.capacityGb() * 2);
        if (drive != null) {
            alternatives.add(alternative(drive, currentPrice, Alternative.Direction.BETTER,
                    "Mais espaço (" + capacityText(drive.part().capacityGb()) + ") para jogos, projetos e arquivos."));
        }
        return alternatives;
    }

    private List<Alternative> boardAlternatives(Pools pools, BuildParts parts) {
        Motherboard current = parts.motherboard();
        BigDecimal currentPrice = pools.price(current);
        if (currentPrice == null || current.hasWifi()) {
            return List.of();
        }
        return pools.boards().stream()
                .filter(board -> board.part().hasWifi()
                        && Fit.socket(parts.cpu(), board.part()) == Fit.Verdict.YES
                        && Fit.generationSupport(parts.cpu(), board.part()) == Fit.Verdict.YES
                        && Objects.equals(board.part().ramType(), current.ramType())
                        && Fit.memorySlots(parts.memory(), board.part()) == Fit.Verdict.YES
                        && Fit.motherboardInCase(board.part(), parts.pcCase()) == Fit.Verdict.YES
                        && parts.storage().stream().allMatch(drive -> !drive.isM2() || Fit.m2DriveOnBoard(drive, board.part()) == Fit.Verdict.YES))
                .findFirst()
                .map(board -> List.of(alternative(board, currentPrice, Alternative.Direction.BETTER,
                        "Adiciona Wi-Fi e Bluetooth, para quem não vai ligar o computador no cabo de rede.")))
                .orElse(List.of());
    }

    private List<Alternative> psuAlternatives(Pools pools, BuildParts parts) {
        PowerSupply current = parts.powerSupply();
        BigDecimal currentPrice = pools.price(current);
        if (currentPrice == null || current.efficiencyRating() == null
                || Set.of("80+ Gold", "80+ Platinum", "80+ Titanium").contains(current.efficiencyRating())) {
            return List.of();
        }
        PowerEstimate power = PowerEstimator.estimate(parts);
        return pools.psus().stream()
                .filter(psu -> "80+ Gold".equals(psu.part().efficiencyRating())
                        && psuFits(psu.part(), power, parts.gpu(), parts.pcCase()))
                .findFirst()
                .map(psu -> List.of(alternative(psu, currentPrice, Alternative.Direction.BETTER,
                        "Certificação 80+ Gold: desperdiça menos energia em calor, esquenta menos e costuma durar mais.")))
                .orElse(List.of());
    }

    private static Alternative alternative(Priced<? extends HardwareComponent> option, BigDecimal currentPrice,
                                           Alternative.Direction direction, String impact) {
        return new Alternative(option.part(), option.price(), option.price().subtract(currentPrice), direction, impact);
    }

    /**
     * Rounded on purpose: these are estimates from specifications, so precise figures would overstate accuracy.
     * Large gains read as multiples ("cerca de 2,5×"), small ones as percentages rounded to 5%.
     */
    static String percentText(double alternative, double current, String metric) {
        double ratio = alternative / current;
        if (ratio >= 1.8) {
            return String.format(Locale.of("pt", "BR"), "Cerca de %.1f× o %s atual (estimativa pelas especificações).",
                    Math.round(ratio * 2) / 2.0, metric);
        }
        long percent = Math.round(Math.abs(ratio - 1) * 20) * 5;
        String direction = ratio < 1 ? "menos" : "mais";
        return "Cerca de " + Math.max(5, percent) + "% " + direction + " " + metric + " (estimativa pelas especificações).";
    }

    static String capacityText(int gb) {
        return gb >= 1000 ? (gb % 1000 == 0 ? gb / 1000 + " TB" : String.format(Locale.ROOT, "%.1f TB", gb / 1000.0)) : gb + " GB";
    }

    // ---------------------------------------------------------------------------------------------
    // Candidate pools
    // ---------------------------------------------------------------------------------------------

    /** Price used by the engine for a catalog part, or {@code null} when no provider has an offer. */
    public Optional<Offer> offer(HardwareComponent component) {
        return prices.bestOffer(component);
    }

    private BuildParts resolveOwned(Catalog catalog, List<UUID> ids) {
        List<HardwareComponent> components = ids.stream()
                .map(id -> catalog.find(id).orElseThrow(() -> new IllegalArgumentException("Peça não encontrada: " + id)))
                .toList();
        return BuildParts.of(components);
    }

    Pools pools(Catalog catalog) {
        Pools pools = cachedPools;
        if (pools == null || pools.catalog() != catalog) {
            synchronized (this) {
                pools = cachedPools;
                if (pools == null || pools.catalog() != catalog) {
                    pools = buildPools(catalog);
                    cachedPools = pools;
                }
            }
        }
        return pools;
    }

    private Pools buildPools(Catalog catalog) {
        Map<UUID, BigDecimal> priceById = new HashMap<>();
        List<Priced<Cpu>> cpus = priced(catalog.all(ComponentCategory.CPU, Cpu.class), CandidatePolicy::cpu, priceById);
        List<Priced<Gpu>> allGpus = priced(catalog.all(ComponentCategory.GPU, Gpu.class), CandidatePolicy::gpu, priceById);
        // Recommend at chip level: for each GPU chip keep the cheapest card (variants differ in cooler and clocks).
        Map<String, Priced<Gpu>> cheapestPerChip = new LinkedHashMap<>();
        for (Priced<Gpu> gpu : allGpus) {
            cheapestPerChip.putIfAbsent(gpu.part().chipset(), gpu);
        }
        List<Priced<Gpu>> gpus = cheapestPerChip.values().stream()
                .sorted(Comparator.comparingDouble((Priced<Gpu> gpu) -> PerformanceEstimator.gpuScore(gpu.part())))
                .toList();
        Pools pools = new Pools(catalog, priceById, cpus, gpus,
                priced(catalog.all(ComponentCategory.MOTHERBOARD, Motherboard.class), CandidatePolicy::motherboard, priceById),
                priced(catalog.all(ComponentCategory.MEMORY, Memory.class), CandidatePolicy::memory, priceById),
                priced(catalog.all(ComponentCategory.STORAGE, Storage.class), CandidatePolicy::bootDrive, priceById),
                priced(catalog.all(ComponentCategory.POWER_SUPPLY, PowerSupply.class), CandidatePolicy::powerSupply, priceById),
                priced(catalog.all(ComponentCategory.CASE, PcCase.class), CandidatePolicy::pcCase, priceById),
                priced(catalog.all(ComponentCategory.CPU_COOLER, CpuCooler.class), CandidatePolicy::cooler, priceById),
                priced(catalog.all(ComponentCategory.STORAGE, Storage.class), CandidatePolicy::sataSsd, priceById),
                ScoreRange.of(gpus.stream().mapToDouble(gpu -> PerformanceEstimator.gpuScore(gpu.part())).toArray()),
                ScoreRange.of(cpus.stream().mapToDouble(cpu -> PerformanceEstimator.cpuMultiThreadScore(cpu.part())).toArray()),
                ScoreRange.of(cpus.stream().mapToDouble(cpu -> PerformanceEstimator.cpuGamingScore(cpu.part())).toArray()));
        return pools;
    }

    /** Eligible parts that have a price, sorted from cheapest. Parts without any offer cannot be budgeted. */
    private <T extends HardwareComponent> List<Priced<T>> priced(List<T> parts, Predicate<T> policy, Map<UUID, BigDecimal> priceById) {
        return parts.stream()
                .filter(policy)
                .map(part -> prices.bestOffer(part).map(offer -> new Priced<>(part, offer.priceBrl())).orElse(null))
                .filter(Objects::nonNull)
                .peek(priced -> priceById.put(priced.part().id(), priced.price()))
                .sorted(Comparator.comparing((Priced<T> priced) -> priced.price()).thenComparing(priced -> priced.part().name()))
                .toList();
    }

    static String brl(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(value);
    }

    record Priced<T extends HardwareComponent>(T part, BigDecimal price) {
    }

    private record Platform(Priced<Motherboard> board, Priced<Memory> memory, List<Priced<Storage>> storage,
                            Priced<CpuCooler> cooler, BigDecimal cost) {
    }

    private record Chassis(Priced<PcCase> pcCase, Priced<PowerSupply> psu, BigDecimal cost) {
    }

    private record Candidate(BuildParts parts, BigDecimal cost, double utility) {

        boolean betterThan(Candidate other) {
            if (Math.abs(utility - other.utility) > 1e-9) {
                return utility > other.utility;
            }
            return cost.compareTo(other.cost) < 0;
        }
    }

    private record Attempt(Candidate best, Candidate cheapest) {
    }

    /** Log-scale position of a score between the pool minimum (0) and maximum (1). */
    record ScoreRange(double min, double max) {

        static ScoreRange of(double[] scores) {
            double min = Math.max(Arrays.stream(scores).min().orElse(1), 1e-6);
            double max = Arrays.stream(scores).max().orElse(1);
            return new ScoreRange(min, Math.max(max, min * 1.0001));
        }

        double normalize(Double score) {
            if (score == null || score <= 0) {
                return 0;
            }
            return Math.max(0, Math.min(1, Math.log(score / min) / Math.log(max / min)));
        }
    }

    record Pools(Catalog catalog, Map<UUID, BigDecimal> priceById, List<Priced<Cpu>> cpus, List<Priced<Gpu>> gpus,
                         List<Priced<Motherboard>> boards, List<Priced<Memory>> memory, List<Priced<Storage>> drives,
                         List<Priced<PowerSupply>> psus, List<Priced<PcCase>> cases, List<Priced<CpuCooler>> coolers,
                         List<Priced<Storage>> sataSsds, ScoreRange gpuRange, ScoreRange cpuMultiRange, ScoreRange cpuGamingRange) {

        BigDecimal price(HardwareComponent component) {
            return priceById.get(component.id());
        }
    }
}
