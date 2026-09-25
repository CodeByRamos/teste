package app.platform.recommendation;

import app.platform.catalog.Catalog;
import app.platform.compatibility.BuildParts;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.compatibility.CompatibilityReport;
import app.platform.compatibility.CompatibilityStatus;
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
import app.platform.recommendation.RecommendationEngine.Pools;
import app.platform.recommendation.RecommendationEngine.Priced;
import app.platform.recommendation.UpgradeAdvice.Change;
import app.platform.recommendation.UpgradeAdvice.Level;
import app.platform.recommendation.UpgradeAdvice.PartAssessment;
import app.platform.recommendation.UpgradeAdvice.UpgradePlan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * "I have this PC and want it better": finds what limits the PC for the person's goals and which set of
 * changes gives the biggest gain within budget.
 *
 * <p>A change is never proposed alone: every plan checks what it forces (power supply, case, motherboard,
 * memory, cooler) using the same {@link Fit} checks as the compatibility engine, and the resulting PC is
 * verified before it is offered.
 */
public final class UpgradeAdvisor {

    /** Minimum estimated improvement for a GPU/CPU swap to be worth suggesting. */
    private static final double MEANINGFUL_SCORE_GAIN = 1.15;
    private static final double MIN_PLAN_GAIN = 0.02;

    private final RecommendationEngine engine;
    private final CompatibilityEngine compatibility;

    public UpgradeAdvisor(RecommendationEngine engine, CompatibilityEngine compatibility) {
        this.engine = engine;
        this.compatibility = compatibility;
    }

    /**
     * @param goals what the person wants to do and how much to spend on the upgrade
     * @param focus optional part the person wants to improve; {@code null} lets the advisor pick
     */
    public UpgradeAdvice advise(Catalog catalog, List<UUID> currentIds, BuildRequest goals, ComponentCategory focus) {
        BuildParts current = BuildParts.of(currentIds.stream()
                .distinct()
                .map(id -> catalog.find(id).orElseThrow(() -> new IllegalArgumentException("Peça não encontrada: " + id)))
                .toList());
        if (current.cpu() == null || current.motherboard() == null) {
            throw new IllegalArgumentException(
                    "Para sugerir upgrades precisamos pelo menos do processador e da placa-mãe do seu PC.");
        }
        RequirementProfile profile = RequirementAnalyzer.analyze(goals);
        Pools pools = engine.pools(catalog);
        BigDecimal budget = goals.budgetBrl();

        List<PartAssessment> assessment = assess(pools, profile, goals, current);
        List<String> notes = new ArrayList<>();
        if (current.powerSupply() == null) {
            notes.add("Você não informou a fonte. Consideramos que ela aguenta o PC atual; confirme a potência antes de comprar peças mais fortes.");
        }
        if (current.pcCase() == null) {
            notes.add("Você não informou o gabinete, então não conseguimos confirmar se peças maiores cabem nele.");
        }

        List<UpgradePlan> plans = new ArrayList<>();
        if (focus == null || focus == ComponentCategory.GPU) {
            gpuPlan(pools, profile, current, budget).ifPresent(plans::add);
        }
        if (focus == null || focus == ComponentCategory.CPU) {
            cpuPlan(pools, profile, current, budget).ifPresent(plans::add);
            platformPlan(pools, profile, current, budget).ifPresent(plans::add);
        }
        if (focus == null || focus == ComponentCategory.MEMORY) {
            memoryPlan(pools, profile, current, budget).ifPresent(plans::add);
        }
        if (focus == null || focus == ComponentCategory.STORAGE) {
            storagePlan(pools, profile, current, budget).ifPresent(plans::add);
        }
        plans.removeIf(plan -> plan.gain() < MIN_PLAN_GAIN || introducesProblems(current, plan.result()));
        plans.sort(Comparator.comparingDouble(UpgradePlan::gain).reversed().thenComparing(UpgradePlan::costBrl));

        if (plans.isEmpty()) {
            notes.add(focus == null
                    ? "Com esse orçamento não encontramos um upgrade que traga ganho relevante para o que você quer fazer."
                    : "Com esse orçamento não encontramos um upgrade de " + focus.label().toLowerCase(Locale.ROOT)
                            + " que traga ganho relevante. Tente outra peça ou um orçamento maior.");
            return new UpgradeAdvice(current, profile, assessment, null, List.of(), notes);
        }
        UpgradePlan recommended = focus == null ? bestCombination(pools, profile, current, plans, budget) : plans.getFirst();
        List<UpgradePlan> alternatives = plans.stream()
                .filter(plan -> plan.kind() != mainKind(recommended))
                .toList();
        BigDecimal leftover = budget.subtract(recommended.costBrl());
        if (leftover.compareTo(budget.multiply(new BigDecimal("0.2"))) > 0) {
            notes.add("A recomendação usa " + RecommendationEngine.brl(recommended.costBrl()) + " de " + RecommendationEngine.brl(budget)
                    + ". Gastar mais agora traria pouco ganho: o resto do PC passaria a limitar o desempenho.");
        }
        return new UpgradeAdvice(current, profile, assessment, recommended, alternatives, notes);
    }

    /**
     * Spends the budget where it helps most: compares each major upgrade alone, each major upgrade chosen with room
     * left for cheap fixes (memory, SSD), and the fixes alone.
     */
    private UpgradePlan bestCombination(Pools pools, RequirementProfile profile, BuildParts current, List<UpgradePlan> singles,
                                        BigDecimal budget) {
        List<UpgradePlan> candidates = new ArrayList<>(singles);
        Optional<UpgradePlan> fixes = singles.stream()
                .filter(plan -> plan.kind() == UpgradePlan.Kind.MEMORY || plan.kind() == UpgradePlan.Kind.STORAGE)
                .findFirst()
                .map(first -> combine(pools, profile, current, first, budget));
        fixes.ifPresent(candidates::add);
        BigDecimal reserved = fixes.map(UpgradePlan::costBrl).orElse(BigDecimal.ZERO);
        for (UpgradePlan single : singles) {
            if (single.kind() == UpgradePlan.Kind.MEMORY || single.kind() == UpgradePlan.Kind.STORAGE) {
                continue;
            }
            candidates.add(combine(pools, profile, current, single, budget));
            if (reserved.signum() > 0) {
                majorPlan(pools, profile, current, single.kind(), budget.subtract(reserved))
                        .filter(plan -> plan.gain() >= MIN_PLAN_GAIN && !introducesProblems(current, plan.result()))
                        .ifPresent(plan -> candidates.add(combine(pools, profile, current, plan, budget)));
            }
        }
        return candidates.stream()
                .filter(plan -> plan.costBrl().compareTo(budget) <= 0)
                .max(Comparator.comparingDouble(UpgradePlan::gain).thenComparing(UpgradePlan::costBrl, Comparator.reverseOrder()))
                .orElse(singles.getFirst());
    }

    private Optional<UpgradePlan> majorPlan(Pools pools, RequirementProfile profile, BuildParts current, UpgradePlan.Kind kind,
                                            BigDecimal budget) {
        return switch (kind) {
            case GPU -> gpuPlan(pools, profile, current, budget);
            case CPU -> cpuPlan(pools, profile, current, budget);
            case PLATFORM -> platformPlan(pools, profile, current, budget);
            default -> Optional.empty();
        };
    }

    /** Kind of the change that drives a plan (for combined plans, the first main change). */
    private static UpgradePlan.Kind mainKind(UpgradePlan plan) {
        if (plan.kind() != UpgradePlan.Kind.COMBINED) {
            return plan.kind();
        }
        return switch (plan.changes().getFirst().category()) {
            case GPU -> UpgradePlan.Kind.GPU;
            case CPU -> plan.changes().stream().anyMatch(change -> change.category() == ComponentCategory.MOTHERBOARD)
                    ? UpgradePlan.Kind.PLATFORM : UpgradePlan.Kind.CPU;
            case MEMORY -> UpgradePlan.Kind.MEMORY;
            default -> UpgradePlan.Kind.STORAGE;
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Assessment of the current PC
    // ---------------------------------------------------------------------------------------------

    private List<PartAssessment> assess(Pools pools, RequirementProfile profile, BuildRequest goals, BuildParts current) {
        List<PartAssessment> result = new ArrayList<>();
        double gpuTier = current.gpu() == null ? 0 : pools.gpuRange().normalize(PerformanceEstimator.gpuScore(current.gpu()));
        double cpuGamingTier = pools.cpuGamingRange().normalize(PerformanceEstimator.cpuGamingScore(current.cpu()));

        // Graphics
        double neededGpuTier = neededGpuTier(goals);
        if (current.gpu() == null) {
            result.add(profile.needsDedicatedGpu()
                    ? new PartAssessment(ComponentCategory.GPU, null, Level.BOTTLENECK, "Sem placa de vídeo dedicada",
                    "Para " + usesText(goals) + ", uma placa de vídeo dedicada faz muita falta: o vídeo integrado não dá conta.")
                    : new PartAssessment(ComponentCategory.GPU, null, Level.GOOD, "Vídeo integrado",
                    "Para o que você quer fazer, o vídeo integrado do processador é suficiente."));
        } else {
            Gpu gpu = current.gpu();
            String target = goals.includesGaming() ? "jogos em " + goals.resolution().label() : usesText(goals);
            if (gpu.vramGb() != null && profile.minVramGb() > 0 && gpu.vramGb() < profile.minVramGb()) {
                result.add(new PartAssessment(ComponentCategory.GPU, gpu, Level.WEAK, "Pouca memória de vídeo",
                        "Tem " + gpu.vramGb() + " GB de memória de vídeo; para " + target + " recomendamos pelo menos "
                                + profile.minVramGb() + " GB. Jogos novos podem travar ou exigir gráficos no mínimo."));
            } else if (gpuTier < neededGpuTier - 0.15) {
                result.add(new PartAssessment(ComponentCategory.GPU, gpu, Level.BOTTLENECK, "É o que mais limita o seu PC",
                        "Pelo que estimamos, fica bem abaixo do necessário para " + target + "."));
            } else if (gpuTier < neededGpuTier) {
                result.add(new PartAssessment(ComponentCategory.GPU, gpu, Level.WEAK, "Um pouco abaixo do ideal",
                        "Dá para usar, mas com qualidade gráfica reduzida em " + target + "."));
            } else {
                result.add(new PartAssessment(ComponentCategory.GPU, gpu, Level.GOOD, "Atende bem",
                        "Tem desempenho adequado para " + target + "."));
            }
        }

        // Processor
        Cpu cpu = current.cpu();
        if (cpu.threads() != null && cpu.threads() < profile.minCpuThreads()) {
            result.add(new PartAssessment(ComponentCategory.CPU, cpu, Level.WEAK, "Poucos núcleos para o seu uso",
                    "Tem " + cpu.threads() + " threads; para " + usesText(goals) + " recomendamos pelo menos " + profile.minCpuThreads() + "."));
        } else if (profile.gamingFocused() && current.gpu() != null && cpuGamingTier < gpuTier - 0.25) {
            result.add(new PartAssessment(ComponentCategory.CPU, cpu, Level.BOTTLENECK, "Segura a placa de vídeo",
                    "Em jogos, este processador não consegue acompanhar a placa de vídeo: parte do desempenho dela fica sem uso."));
        } else {
            result.add(new PartAssessment(ComponentCategory.CPU, cpu, Level.GOOD, "Atende bem",
                    "Tem núcleos e velocidade suficientes para " + usesText(goals) + "."));
        }

        // Memory
        Memory memory = current.memory();
        if (memory == null || memory.totalCapacityGb() == null) {
            result.add(new PartAssessment(ComponentCategory.MEMORY, memory, Level.ENOUGH, "Não informada",
                    "Sem saber a memória atual, não conseguimos avaliar se ela é suficiente."));
        } else if (memory.totalCapacityGb() < profile.minRamGb()) {
            result.add(new PartAssessment(ComponentCategory.MEMORY, memory, Level.BOTTLENECK, "Pouca memória",
                    memory.totalCapacityGb() + " GB é pouco para " + usesText(goals) + ": recomendamos pelo menos "
                            + profile.minRamGb() + " GB. Isso costuma causar travamentos."));
        } else if (memory.totalCapacityGb() < profile.targetRamGb()) {
            result.add(new PartAssessment(ComponentCategory.MEMORY, memory, Level.ENOUGH, "Suficiente",
                    memory.totalCapacityGb() + " GB atende, mas " + profile.targetRamGb() + " GB dariam mais folga."));
        } else {
            result.add(new PartAssessment(ComponentCategory.MEMORY, memory, Level.GOOD, "Atende bem",
                    memory.totalCapacityGb() + " GB é uma boa quantidade para o seu uso."));
        }

        // Storage
        List<Storage> drives = current.storage();
        int capacity = drives.stream().mapToInt(drive -> Objects.requireNonNullElse(drive.capacityGb(), 0)).sum();
        if (drives.isEmpty()) {
            result.add(new PartAssessment(ComponentCategory.STORAGE, null, Level.ENOUGH, "Não informado",
                    "Sem saber o armazenamento atual, não conseguimos avaliar."));
        } else if (drives.stream().noneMatch(Storage::isSsd)) {
            result.add(new PartAssessment(ComponentCategory.STORAGE, drives.getFirst(), Level.BOTTLENECK, "Sem SSD",
                    "Um HD tradicional deixa tudo lento para ligar e abrir. Um SSD é o upgrade que mais se sente no dia a dia."));
        } else if (capacity < profile.minStorageGb()) {
            result.add(new PartAssessment(ComponentCategory.STORAGE, drives.getFirst(), Level.WEAK, "Pouco espaço",
                    ComponentExplainer.capacity(capacity) + " é pouco para " + usesText(goals) + "."));
        } else {
            result.add(new PartAssessment(ComponentCategory.STORAGE, drives.getFirst(), Level.GOOD, "Atende bem",
                    "Tem SSD e " + ComponentExplainer.capacity(capacity) + " de espaço."));
        }

        // Power supply
        PowerSupply psu = current.powerSupply();
        PowerEstimate power = PowerEstimator.estimate(current);
        if (psu == null || psu.wattage() == null) {
            result.add(new PartAssessment(ComponentCategory.POWER_SUPPLY, psu, Level.ENOUGH, "Não informada",
                    "Sem saber a fonte, não conseguimos confirmar se ela aguenta peças mais fortes."));
        } else if (psu.wattage() < power.recommendedPsuWatts()) {
            result.add(new PartAssessment(ComponentCategory.POWER_SUPPLY, psu, Level.WEAK, "Pouca folga",
                    "Com " + psu.wattage() + " W, ela trabalha no limite (recomendado: " + power.recommendedPsuWatts()
                            + " W). Um upgrade de placa de vídeo provavelmente exigirá fonte nova."));
        } else {
            result.add(new PartAssessment(ComponentCategory.POWER_SUPPLY, psu, Level.GOOD, "Atende bem",
                    "Tem " + psu.wattage() + " W para um consumo estimado de " + power.estimatedLoadWatts() + " W."));
        }

        // Anything the compatibility engine flags in the current PC
        CompatibilityReport report = compatibility.check(current);
        report.findings().stream()
                .filter(finding -> finding.status() == CompatibilityStatus.INCOMPATIBLE)
                .forEach(finding -> result.add(new PartAssessment(finding.involves().getFirst(), null, Level.BOTTLENECK,
                        finding.title(), finding.explanation())));
        return result;
    }

    private static double neededGpuTier(BuildRequest goals) {
        if (goals.useCases().contains(UseCase.GAMING_AAA)) {
            return switch (goals.resolution()) {
                case FULL_HD -> 0.45;
                case QHD -> 0.6;
                case UHD_4K -> 0.75;
            };
        }
        if (goals.useCases().contains(UseCase.GAMING_COMPETITIVE)) {
            return 0.35;
        }
        return goals.useCases().contains(UseCase.VIDEO_EDITING) || goals.useCases().contains(UseCase.STREAMING) ? 0.3 : 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Plans
    // ---------------------------------------------------------------------------------------------

    private Optional<UpgradePlan> gpuPlan(Pools pools, RequirementProfile profile, BuildParts current, BigDecimal budget) {
        Gpu old = current.gpu();
        double oldScore = old == null ? 0 : orZero(PerformanceEstimator.gpuScore(old));
        if (Fit.gpuSlot(current.motherboard()) == Fit.Verdict.NO) {
            return Optional.empty();
        }
        UpgradePlan best = null;
        for (Priced<Gpu> candidate : pools.gpus()) {
            Gpu gpu = candidate.part();
            if (PerformanceEstimator.gpuScore(gpu) < Math.max(oldScore * MEANINGFUL_SCORE_GAIN, 1)
                    || profile.minVramGb() > 0 && gpu.vramGb() < profile.minVramGb()) {
                continue;
            }
            PlanBuilder plan = new PlanBuilder(current);
            plan.change(ComponentCategory.GPU, old, candidate, Change.Role.MAIN,
                    old == null ? "Adiciona uma placa de vídeo dedicada." : "Substitui a " + old.name() + ".");
            plan.dependency("A nova placa consome até " + gpu.tdpWatts() + " W.");
            if (!ensureCaseFits(pools, plan, gpu) || !ensurePower(pools, plan)) {
                continue;
            }
            if (profile.gamingFocused()) {
                double gpuTier = pools.gpuRange().normalize(PerformanceEstimator.gpuScore(gpu));
                double cpuTier = pools.cpuGamingRange().normalize(PerformanceEstimator.cpuGamingScore(current.cpu()));
                if (gpuTier - cpuTier > 0.3) {
                    plan.dependency("Seu processador pode limitar parte do ganho em jogos; trocá-lo depois libera o resto do desempenho.");
                }
            }
            String impact = old == null
                    ? "Adiciona uma placa de vídeo dedicada: permite jogar e acelera edição e lives."
                    : RecommendationEngine.percentText(PerformanceEstimator.gpuScore(gpu), oldScore, "desempenho gráfico");
            UpgradePlan built = plan.build(UpgradePlan.Kind.GPU, old == null ? "Adicionar uma placa de vídeo" : "Trocar a placa de vídeo",
                    impact, pools, profile);
            best = better(best, built, budget);
        }
        return Optional.ofNullable(best);
    }

    private Optional<UpgradePlan> cpuPlan(Pools pools, RequirementProfile profile, BuildParts current, BigDecimal budget) {
        Cpu old = current.cpu();
        Motherboard board = current.motherboard();
        UpgradePlan best = null;
        for (Priced<Cpu> candidate : pools.cpus()) {
            Cpu cpu = candidate.part();
            if (cpu.id().equals(old.id())
                    || Fit.socket(cpu, board) != Fit.Verdict.YES
                    || Fit.generationSupport(cpu, board) != Fit.Verdict.YES
                    || Fit.cpuSupportsBoardMemory(cpu, board) == Fit.Verdict.NO
                    || current.gpu() == null && !Boolean.TRUE.equals(cpu.integratedGraphics())
                    || !meaningfullyFaster(profile, cpu, old)) {
                continue;
            }
            PlanBuilder plan = new PlanBuilder(current);
            plan.change(ComponentCategory.CPU, old, candidate, Change.Role.MAIN, "Substitui o " + old.name() + ".");
            plan.dependency("Usa o mesmo encaixe (" + board.socket() + ") da sua placa-mãe: placa-mãe e memória continuam.");
            if (!ensureCooling(pools, plan, cpu) || !ensurePower(pools, plan)) {
                continue;
            }
            UpgradePlan built = plan.build(UpgradePlan.Kind.CPU, "Trocar o processador", cpuImpact(profile, cpu, old), pools, profile);
            best = better(best, built, budget);
        }
        return Optional.ofNullable(best);
    }

    private Optional<UpgradePlan> platformPlan(Pools pools, RequirementProfile profile, BuildParts current, BigDecimal budget) {
        Cpu old = current.cpu();
        UpgradePlan best = null;
        for (Priced<Cpu> candidate : pools.cpus()) {
            Cpu cpu = candidate.part();
            if (Fit.socket(cpu, current.motherboard()) == Fit.Verdict.YES
                    || current.gpu() == null && !Boolean.TRUE.equals(cpu.integratedGraphics())
                    || !meaningfullyFaster(profile, cpu, old)) {
                continue;
            }
            PlanBuilder plan = new PlanBuilder(current);
            plan.change(ComponentCategory.CPU, old, candidate, Change.Role.MAIN, "Substitui o " + old.name() + ".");
            plan.dependency("O novo processador usa outro encaixe (" + cpu.socket() + "), então a placa-mãe precisa ser trocada.");
            if (!choosePlatform(pools, profile, plan, cpu) || !ensureCooling(pools, plan, cpu) || !ensurePower(pools, plan)) {
                continue;
            }
            String title = plan.replaces(ComponentCategory.MEMORY)
                    ? "Trocar processador, placa-mãe e memória"
                    : "Trocar processador e placa-mãe";
            UpgradePlan built = plan.build(UpgradePlan.Kind.PLATFORM, title, cpuImpact(profile, cpu, old), pools, profile);
            best = better(best, built, budget);
        }
        return Optional.ofNullable(best);
    }

    private Optional<UpgradePlan> memoryPlan(Pools pools, RequirementProfile profile, BuildParts current, BigDecimal budget) {
        Memory old = current.memory();
        if (old != null && old.totalCapacityGb() != null && old.totalCapacityGb() >= profile.targetRamGb()) {
            return Optional.empty();
        }
        int target = Math.max(profile.targetRamGb(), old == null || old.totalCapacityGb() == null ? 0 : old.totalCapacityGb() * 2);
        Optional<UpgradePlan> plan = memoryPlanFor(pools, profile, current, target, budget);
        if (plan.isEmpty() && profile.minRamGb() < target) {
            plan = memoryPlanFor(pools, profile, current, profile.minRamGb(), budget);
        }
        return plan;
    }

    private Optional<UpgradePlan> memoryPlanFor(Pools pools, RequirementProfile profile, BuildParts current, int capacityGb, BigDecimal budget) {
        Memory old = current.memory();
        Priced<Memory> kit = RecommendationEngine.cheapestMemory(pools, current.motherboard(), capacityGb);
        if (kit == null || old != null && old.totalCapacityGb() != null && kit.part().totalCapacityGb() <= old.totalCapacityGb()) {
            return Optional.empty();
        }
        PlanBuilder plan = new PlanBuilder(current);
        plan.change(ComponentCategory.MEMORY, old, kit, Change.Role.MAIN,
                old == null ? "Memória para o seu PC." : "Substitui os " + old.totalCapacityGb() + " GB atuais.");
        plan.dependency("Mesmo tipo de memória da placa-mãe (" + current.motherboard().ramType() + "), em dois pentes iguais para trabalhar em dual channel.");
        String impact = (old == null || old.totalCapacityGb() == null ? "" : "De " + old.totalCapacityGb() + " GB para ")
                + kit.part().totalCapacityGb() + " GB: mais programas e abas abertos sem travar.";
        UpgradePlan built = plan.build(UpgradePlan.Kind.MEMORY, "Aumentar a memória RAM", capitalize(impact), pools, profile);
        return built.costBrl().compareTo(budget) <= 0 ? Optional.of(built) : Optional.empty();
    }

    private Optional<UpgradePlan> storagePlan(Pools pools, RequirementProfile profile, BuildParts current, BigDecimal budget) {
        boolean hasSsd = current.storage().stream().anyMatch(Storage::isSsd);
        int capacity = current.storage().stream().mapToInt(drive -> Objects.requireNonNullElse(drive.capacityGb(), 0)).sum();
        if (hasSsd && capacity >= profile.minStorageGb()) {
            return Optional.empty();
        }
        Motherboard board = current.motherboard();
        long usedM2 = current.storage().stream().filter(Storage::isM2).count();
        long usedSata = current.storage().stream().filter(Storage::usesSataPort).count();
        int wanted = Math.max(500, profile.minStorageGb());
        Priced<Storage> drive = null;
        String how;
        if (board.m2Slots() != null && board.driveM2Slots().size() > usedM2) {
            drive = RecommendationEngine.cheapestDrive(pools, board, wanted);
            how = "Vai num encaixe M.2 livre da sua placa-mãe.";
        } else {
            how = "Liga numa porta SATA livre da placa-mãe, com o cabo SATA (e um da fonte).";
        }
        if (drive == null && (board.sataPorts() == null || board.sataPorts() > usedSata)) {
            drive = pools.sataSsds().stream().filter(ssd -> ssd.part().capacityGb() >= wanted).findFirst().orElse(null);
            how = "Liga numa porta SATA livre da placa-mãe, com o cabo SATA (e um da fonte).";
        }
        if (drive == null) {
            return Optional.empty();
        }
        PlanBuilder plan = new PlanBuilder(current);
        plan.add(ComponentCategory.STORAGE, drive, "Adiciona um SSD; o disco atual pode continuar guardando arquivos.");
        plan.dependency(how);
        if (!hasSsd) {
            plan.dependency("Instale o sistema no SSD novo: é aí que está o ganho de velocidade.");
        }
        UpgradePlan built = plan.build(UpgradePlan.Kind.STORAGE, hasSsd ? "Adicionar mais espaço (SSD)" : "Adicionar um SSD",
                hasSsd ? "Mais " + ComponentExplainer.capacity(drive.part().capacityGb()) + " de espaço rápido."
                        : "O computador passa a ligar em segundos e os programas abrem muito mais rápido.",
                pools, profile);
        return built.costBrl().compareTo(budget) <= 0 ? Optional.of(built) : Optional.empty();
    }

    /**
     * Main plan plus cheap independent fixes (memory, SSD) when the budget still allows. When a fix replaces a
     * part the main plan was already buying (e.g. more memory on a new platform), the two purchases are merged.
     */
    private UpgradePlan combine(Pools pools, RequirementProfile profile, BuildParts current, UpgradePlan main, BigDecimal budget) {
        List<Change> changes = new ArrayList<>(main.changes());
        List<String> dependencies = new ArrayList<>(main.dependencies());
        List<String> extras = new ArrayList<>();
        BuildParts result = main.result();
        BigDecimal cost = main.costBrl();
        for (UpgradePlan.Kind kind : List.of(UpgradePlan.Kind.MEMORY, UpgradePlan.Kind.STORAGE)) {
            if (kind == main.kind()) {
                continue;
            }
            BigDecimal left = budget.subtract(cost);
            Optional<UpgradePlan> extra = kind == UpgradePlan.Kind.MEMORY
                    ? memoryPlan(pools, profile, result, left.add(purchasedPrice(changes, ComponentCategory.MEMORY)))
                    : storagePlan(pools, profile, result, left);
            if (extra.isEmpty() || extra.get().gain() < MIN_PLAN_GAIN || introducesProblems(current, extra.get().result())) {
                continue;
            }
            UpgradePlan add = extra.get();
            for (Change change : add.changes()) {
                Optional<Change> alreadyBought = change.replaces() == null ? Optional.empty() : changes.stream()
                        .filter(existing -> existing.part().id().equals(change.replaces().id()))
                        .findFirst();
                if (alreadyBought.isPresent()) {
                    Change previous = alreadyBought.get();
                    changes.set(changes.indexOf(previous), new Change(change.category(), previous.replaces(), change.part(),
                            previous.role(), change.priceBrl(), previous.reason()));
                    cost = cost.subtract(previous.priceBrl()).add(change.priceBrl());
                } else {
                    changes.add(change);
                    cost = cost.add(change.priceBrl());
                    extras.add(kind == UpgradePlan.Kind.MEMORY ? "mais memória RAM" : "um SSD");
                }
            }
            if (cost.compareTo(budget) > 0) {
                return main;
            }
            dependencies.addAll(add.dependencies());
            result = add.result();
        }
        if (result == main.result()) {
            return main;
        }
        String title = extras.isEmpty() ? main.title() : main.title() + ", com " + String.join(" e ", extras);
        return new UpgradePlan(UpgradePlan.Kind.COMBINED, title, main.impact(), changes, result, cost,
                score(pools, profile, result) - score(pools, profile, current), dependencies);
    }

    private static BigDecimal purchasedPrice(List<Change> changes, ComponentCategory category) {
        return changes.stream().filter(change -> change.category() == category).map(Change::priceBrl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------------------------------------
    // Dependencies forced by a change
    // ---------------------------------------------------------------------------------------------

    private boolean ensureCaseFits(Pools pools, PlanBuilder plan, Gpu gpu) {
        PcCase pcCase = plan.parts.pcCase();
        if (pcCase == null) {
            plan.dependency("Confirme que seu gabinete comporta uma placa de " + gpu.lengthMm() + " mm de comprimento.");
            return true;
        }
        Fit.Verdict fits = Fit.gpuLengthInCase(gpu, pcCase);
        if (fits == Fit.Verdict.YES) {
            plan.dependency("A placa tem " + gpu.lengthMm() + " mm e cabe no seu gabinete (até " + pcCase.maxGpuLengthMm() + " mm).");
            return true;
        }
        if (fits == Fit.Verdict.UNKNOWN) {
            plan.dependency("O fabricante do seu gabinete não informa o comprimento máximo: confirme que cabe uma placa de " + gpu.lengthMm() + " mm.");
            return true;
        }
        Priced<PcCase> newCase = pools.cases().stream()
                .filter(candidate -> RecommendationEngine.caseFits(candidate.part(), plan.parts.motherboard(), gpu, plan.parts.cooler()))
                .findFirst().orElse(null);
        if (newCase == null) {
            return false;
        }
        plan.change(ComponentCategory.CASE, pcCase, newCase, Change.Role.REQUIRED,
                "A placa de vídeo tem " + gpu.lengthMm() + " mm e seu gabinete comporta só " + pcCase.maxGpuLengthMm() + " mm.");
        plan.dependency("Ela não cabe no seu gabinete (até " + pcCase.maxGpuLengthMm() + " mm), então incluímos um gabinete maior.");
        return true;
    }

    private boolean ensurePower(Pools pools, PlanBuilder plan) {
        BuildParts parts = plan.parts;
        PowerEstimate power = PowerEstimator.estimate(parts);
        PowerSupply psu = parts.powerSupply();
        if (psu == null) {
            plan.dependency("Confirme que sua fonte tem pelo menos " + power.recommendedPsuWatts() + " W"
                    + (parts.gpu() == null ? "." : " e os conectores da placa de vídeo."));
            return true;
        }
        boolean enoughWatts = psu.wattage() != null && psu.wattage() >= power.recommendedPsuWatts();
        boolean connectors = parts.gpu() == null || Fit.psuConnectorsForGpu(psu, parts.gpu()) != Fit.Verdict.NO;
        if (enoughWatts && connectors) {
            plan.dependency("Consumo estimado depois do upgrade: " + power.estimatedLoadWatts() + " W. Sua fonte de "
                    + psu.wattage() + " W continua servindo.");
            return true;
        }
        Priced<PowerSupply> newPsu = pools.psus().stream()
                .filter(candidate -> parts.pcCase() == null
                        ? candidate.part().wattage() >= power.recommendedPsuWatts()
                        : RecommendationEngine.psuFits(candidate.part(), power, parts.gpu(), parts.pcCase()))
                .filter(candidate -> parts.gpu() == null || Fit.psuConnectorsForGpu(candidate.part(), parts.gpu()) == Fit.Verdict.YES)
                .findFirst().orElse(null);
        if (newPsu == null) {
            return false;
        }
        String why = !enoughWatts
                ? "O consumo estimado sobe para " + power.estimatedLoadWatts() + " W, e sua fonte de " + psu.wattage()
                        + " W não teria folga (recomendado: " + power.recommendedPsuWatts() + " W)."
                : "Sua fonte não tem os cabos de energia que a nova placa de vídeo usa.";
        plan.change(ComponentCategory.POWER_SUPPLY, psu, newPsu, Change.Role.REQUIRED, why);
        plan.dependency(why + " Por isso incluímos uma fonte nova.");
        return true;
    }

    private boolean ensureCooling(Pools pools, PlanBuilder plan, Cpu cpu) {
        CpuCooler cooler = plan.parts.cooler();
        int minHeight = RecommendationEngine.minimumCoolerHeight(cpu);
        if (cooler != null && Fit.coolerSocket(cooler, cpu) == Fit.Verdict.YES
                && (!cooler.isAirCooler() || cooler.heightMm() != null && cooler.heightMm() >= minHeight)) {
            plan.dependency("Seu cooler atual serve no novo processador.");
            return true;
        }
        if (RecommendationEngine.stockCoolerIsEnough(cpu)) {
            if (cooler != null) {
                plan.parts = plan.parts.withCooler(null);
                plan.dependency("O novo processador vem com cooler na caixa; use-o no lugar do atual.");
            } else {
                plan.dependency("O novo processador vem com cooler na caixa.");
            }
            return true;
        }
        Priced<CpuCooler> newCooler = pools.coolers().stream()
                .filter(candidate -> Fit.coolerSocket(candidate.part(), cpu) == Fit.Verdict.YES
                        && candidate.part().heightMm() >= minHeight
                        && (plan.parts.pcCase() == null || Fit.coolerHeightInCase(candidate.part(), plan.parts.pcCase()) == Fit.Verdict.YES))
                .findFirst().orElse(null);
        if (newCooler == null) {
            return false;
        }
        String why = cooler == null
                ? "Este processador é vendido sem cooler (ou precisa de um maior que o de fábrica)."
                : "Seu cooler atual não prende neste processador ou é pequeno para o consumo dele.";
        plan.change(ComponentCategory.CPU_COOLER, cooler, newCooler, Change.Role.REQUIRED, why);
        plan.dependency(why + " Incluímos um cooler adequado.");
        return true;
    }

    /** New motherboard (and memory if the type changes), keeping drives and case when they still fit. */
    private boolean choosePlatform(Pools pools, RequirementProfile profile, PlanBuilder plan, Cpu cpu) {
        BuildParts parts = plan.parts;
        Memory oldMemory = parts.memory();
        Priced<Motherboard> bestBoard = null;
        Priced<Memory> bestMemory = null;
        BigDecimal bestCost = null;
        for (String ramType : List.of("DDR4", "DDR5")) {
            Priced<Motherboard> board = pools.boards().stream()
                    .filter(candidate -> ramType.equals(candidate.part().ramType())
                            && Fit.socket(cpu, candidate.part()) == Fit.Verdict.YES
                            && Fit.generationSupport(cpu, candidate.part()) == Fit.Verdict.YES
                            && Fit.cpuSupportsBoardMemory(cpu, candidate.part()) == Fit.Verdict.YES
                            && (parts.pcCase() == null || Fit.motherboardInCase(candidate.part(), parts.pcCase()) == Fit.Verdict.YES)
                            && drivesStillConnect(parts.storage(), candidate.part()))
                    .findFirst().orElse(null);
            if (board == null) {
                continue;
            }
            Priced<Memory> memory = null;
            boolean keepMemory = oldMemory != null && ramType.equals(oldMemory.ramType())
                    && Fit.memorySlots(oldMemory, board.part()) == Fit.Verdict.YES;
            if (!keepMemory) {
                int capacity = Math.max(profile.minRamGb(), oldMemory == null || oldMemory.totalCapacityGb() == null ? 0 : oldMemory.totalCapacityGb());
                memory = RecommendationEngine.cheapestMemory(pools, board.part(), capacity);
                if (memory == null) {
                    continue;
                }
            }
            BigDecimal cost = board.price().add(memory == null ? BigDecimal.ZERO : memory.price());
            if (bestCost == null || cost.compareTo(bestCost) < 0) {
                bestBoard = board;
                bestMemory = memory;
                bestCost = cost;
            }
        }
        if (bestBoard == null) {
            return false;
        }
        plan.change(ComponentCategory.MOTHERBOARD, parts.motherboard(), bestBoard, Change.Role.REQUIRED,
                "Placa-mãe com o encaixe " + cpu.socket() + " do novo processador.");
        if (bestMemory != null) {
            plan.change(ComponentCategory.MEMORY, oldMemory, bestMemory, Change.Role.REQUIRED,
                    "A nova placa-mãe usa memória " + bestBoard.part().ramType() + (oldMemory == null ? "." : ", diferente da atual."));
            plan.dependency("A nova placa-mãe usa memória " + bestBoard.part().ramType() + ", então a memória também é trocada.");
        } else {
            plan.dependency("A nova placa-mãe usa o mesmo tipo de memória (" + bestBoard.part().ramType() + "): sua memória continua.");
        }
        if (!parts.storage().isEmpty()) {
            plan.dependency("Seus discos continuam: a nova placa-mãe tem onde ligá-los.");
        }
        if (parts.pcCase() != null) {
            plan.dependency("A nova placa-mãe (" + bestBoard.part().formFactor() + ") cabe no seu gabinete.");
        }
        return true;
    }

    private static boolean drivesStillConnect(List<Storage> drives, Motherboard board) {
        long m2 = drives.stream().filter(Storage::isM2).count();
        long sata = drives.stream().filter(Storage::usesSataPort).count();
        boolean m2Ok = drives.stream().filter(Storage::isM2).allMatch(drive -> Fit.m2DriveOnBoard(drive, board) == Fit.Verdict.YES)
                && (m2 == 0 || board.m2Slots() != null && board.driveM2Slots().size() >= m2);
        boolean sataOk = sata == 0 || board.sataPorts() == null || board.sataPorts() >= sata;
        return m2Ok && sataOk;
    }

    // ---------------------------------------------------------------------------------------------
    // Scoring and helpers
    // ---------------------------------------------------------------------------------------------

    /** How well a whole PC serves the profile: processing/graphics utility plus memory and storage adequacy. */
    static double score(Pools pools, RequirementProfile profile, BuildParts parts) {
        double score = parts.cpu() == null ? 0 : RecommendationEngine.utility(pools, profile, parts.cpu(), parts.gpu());
        Memory memory = parts.memory();
        if (memory != null && memory.totalCapacityGb() != null) {
            score += 0.25 * Math.min(1.0, memory.totalCapacityGb() / (double) profile.targetRamGb());
            if (memory.totalCapacityGb() < profile.minRamGb()) {
                score -= 0.3;
            }
        }
        if (parts.storage().stream().anyMatch(Storage::isSsd)) {
            score += 0.25;
        }
        int capacity = parts.storage().stream().mapToInt(drive -> Objects.requireNonNullElse(drive.capacityGb(), 0)).sum();
        if (!parts.storage().isEmpty() && capacity < profile.minStorageGb()) {
            score -= 0.15;
        }
        return score;
    }

    private static boolean meaningfullyFaster(RequirementProfile profile, Cpu candidate, Cpu old) {
        double gaming = orZero(PerformanceEstimator.cpuGamingScore(candidate)) / Math.max(1e-6, orZero(PerformanceEstimator.cpuGamingScore(old)));
        double multi = orZero(PerformanceEstimator.cpuMultiThreadScore(candidate)) / Math.max(1e-6, orZero(PerformanceEstimator.cpuMultiThreadScore(old)));
        double relevant = profile.gamingFocused() ? gaming : Math.max(gaming, multi);
        return relevant >= MEANINGFUL_SCORE_GAIN;
    }

    private static String cpuImpact(RequirementProfile profile, Cpu cpu, Cpu old) {
        boolean gaming = profile.gamingFocused();
        return RecommendationEngine.percentText(
                orZero(gaming ? PerformanceEstimator.cpuGamingScore(cpu) : PerformanceEstimator.cpuMultiThreadScore(cpu)),
                Math.max(1e-6, orZero(gaming ? PerformanceEstimator.cpuGamingScore(old) : PerformanceEstimator.cpuMultiThreadScore(old))),
                gaming ? "desempenho do processador em jogos" : "desempenho com vários programas");
    }

    private static UpgradePlan better(UpgradePlan best, UpgradePlan candidate, BigDecimal budget) {
        if (candidate.costBrl().compareTo(budget) > 0) {
            return best;
        }
        if (best == null || candidate.gain() > best.gain() + 1e-9
                || Math.abs(candidate.gain() - best.gain()) <= 1e-9 && candidate.costBrl().compareTo(best.costBrl()) < 0) {
            return candidate;
        }
        return best;
    }

    /** True when the upgraded PC has an incompatibility the current PC did not already have. */
    private boolean introducesProblems(BuildParts current, BuildParts result) {
        java.util.Set<String> existing = incompatibilities(current);
        return incompatibilities(result).stream().anyMatch(problem -> !existing.contains(problem));
    }

    private java.util.Set<String> incompatibilities(BuildParts parts) {
        return compatibility.check(parts).findings().stream()
                .filter(finding -> finding.status() == CompatibilityStatus.INCOMPATIBLE)
                .map(finding -> finding.ruleId() + "|" + finding.title())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String usesText(BuildRequest goals) {
        List<String> labels = goals.useCases().stream().sorted().map(use -> use.label().toLowerCase(Locale.ROOT)).toList();
        return labels.size() == 1 ? labels.getFirst()
                : String.join(", ", labels.subList(0, labels.size() - 1)) + " e " + labels.getLast();
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Accumulates changes on top of the current PC while a plan is being evaluated. */
    private static final class PlanBuilder {

        private final BuildParts before;
        private BuildParts parts;
        private final List<Change> changes = new ArrayList<>();
        private final List<String> dependencies = new ArrayList<>();
        private BigDecimal cost = BigDecimal.ZERO;

        PlanBuilder(BuildParts current) {
            this.before = current;
            this.parts = current;
        }

        void change(ComponentCategory category, HardwareComponent replaced, Priced<? extends HardwareComponent> part,
                    Change.Role role, String reason) {
            changes.add(new Change(category, replaced, part.part(), role, part.price(), reason));
            parts = parts.replacing(part.part());
            cost = cost.add(part.price());
        }

        void add(ComponentCategory category, Priced<? extends HardwareComponent> part, String reason) {
            changes.add(new Change(category, null, part.part(), Change.Role.MAIN, part.price(), reason));
            parts = parts.with(part.part());
            cost = cost.add(part.price());
        }

        boolean replaces(ComponentCategory category) {
            return changes.stream().anyMatch(change -> change.category() == category);
        }

        void dependency(String text) {
            dependencies.add(text);
        }

        UpgradePlan build(UpgradePlan.Kind kind, String title, String impact, Pools pools, RequirementProfile profile) {
            double gain = score(pools, profile, parts) - score(pools, profile, before);
            return new UpgradePlan(kind, title, impact, changes, parts, cost, gain, dependencies);
        }
    }
}
