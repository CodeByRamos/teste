package app.platform.recommendation;

import app.platform.catalog.Catalog;
import app.platform.compatibility.BuildParts;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.compatibility.CompatibilityFinding;
import app.platform.compatibility.CompatibilityReport;
import app.platform.compatibility.CompatibilityStatus;
import app.platform.compatibility.Fit;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.Cpu;
import app.platform.hardware.Gpu;
import app.platform.hardware.Hardware;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.Storage;
import app.platform.recommendation.FutureOutlook.Aspect;
import app.platform.recommendation.FutureOutlook.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static app.platform.hardware.ComponentCategory.CASE;
import static app.platform.hardware.ComponentCategory.CPU;
import static app.platform.hardware.ComponentCategory.CPU_COOLER;
import static app.platform.hardware.ComponentCategory.GPU;
import static app.platform.hardware.ComponentCategory.MEMORY;
import static app.platform.hardware.ComponentCategory.MOTHERBOARD;
import static app.platform.hardware.ComponentCategory.POWER_SUPPLY;
import static app.platform.hardware.ComponentCategory.STORAGE;

/**
 * "Thinking about the future": for each part that people usually upgrade, finds the best catalog part
 * that drops into this build without any new compatibility problem, and says what blocks going further.
 *
 * <p>Deterministic: example parts are real catalog records checked by the {@link CompatibilityEngine};
 * platform facts (newest CPUs per socket, memory generation) come from catalog release years.
 */
public final class FutureOutlookAnalyzer {

    static final double CLEAR_CPU_GAIN = 1.3;
    static final double CLEAR_GPU_GAIN = 1.5;
    static final double SMALL_GAIN = 1.1;
    /** Candidates must beat the current part by at least this much to count as an upgrade. */
    private static final double MIN_UPGRADE = 1.05;
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final CompatibilityEngine compatibility;

    public FutureOutlookAnalyzer(CompatibilityEngine compatibility) {
        this.compatibility = compatibility;
    }

    /** {@code null} when the build has no motherboard or does not work as it is — there is no base to evolve from. */
    public FutureOutlook analyze(Catalog catalog, BuildParts parts, RequirementProfile profile) {
        if (parts.motherboard() == null) {
            return null;
        }
        CompatibilityReport baseline = compatibility.check(parts);
        if (!baseline.isBuildable()) {
            return null;
        }
        PlatformTimeline timeline = PlatformTimeline.of(catalog);
        List<Aspect> aspects = new ArrayList<>();
        cpuPath(catalog, parts, profile, baseline, timeline).ifPresent(aspects::add);
        gpuPath(catalog, parts, baseline).ifPresent(aspects::add);
        memoryRoom(parts).ifPresent(aspects::add);
        memoryGeneration(parts, timeline).ifPresent(aspects::add);
        storageRoom(parts).ifPresent(aspects::add);
        return aspects.isEmpty() ? null : new FutureOutlook(summary(aspects), aspects);
    }

    // ---------------------------------------------------------------------------------------------
    // Processor
    // ---------------------------------------------------------------------------------------------

    private Optional<Aspect> cpuPath(Catalog catalog, BuildParts parts, RequirementProfile profile,
                                     CompatibilityReport baseline, PlatformTimeline timeline) {
        Cpu cpu = parts.cpu();
        Motherboard board = parts.motherboard();
        if (cpu == null) {
            return Optional.empty();
        }
        boolean gaming = profile == null || profile.gamingFocused();
        ToDoubleFunction<Cpu> score = candidate -> orZero(gaming
                ? PerformanceEstimator.cpuGamingScore(candidate)
                : PerformanceEstimator.cpuMultiThreadScore(candidate));
        double current = score.applyAsDouble(cpu);
        if (current <= 0) {
            return Optional.empty();
        }
        List<Cpu> candidates = catalog.all(CPU, Cpu.class).stream()
                .filter(CandidatePolicy::consumerCpu)
                .filter(candidate -> Fit.socket(candidate, board) == Fit.Verdict.YES)
                .filter(candidate -> score.applyAsDouble(candidate) > current * MIN_UPGRADE)
                .sorted(Comparator.comparingDouble(score).reversed().thenComparing(Cpu::name))
                .toList();
        Cpu best = null;
        boolean biosUpdate = false;
        List<CompatibilityFinding> blockers = List.of();
        for (Cpu candidate : candidates) {
            List<CompatibilityFinding> problems = newProblems(baseline, parts.withCpu(candidate));
            // A possible BIOS update is a caveat, not a part to replace.
            List<CompatibilityFinding> blocking = problems.stream().filter(problem -> !isBiosCaveat(problem)).toList();
            if (blocking.isEmpty()) {
                best = candidate;
                biosUpdate = problems.size() > blocking.size();
                break;
            }
            if (blockers.isEmpty()) {
                blockers = blocking;
            }
        }

        String socket = Hardware.normalizeSocket(board.socket());
        String metric = gaming ? "em jogos" : "com vários programas";
        String limiter = limiterText(blockers);
        double ratio = best == null ? 1 : score.applyAsDouble(best) / current;
        Level level = ratio >= CLEAR_CPU_GAIN ? Level.GOOD : ratio >= SMALL_GAIN ? Level.PARTIAL : Level.LIMITED;
        String headline = switch (level) {
            case GOOD -> "Dá para trocar só o processador e ter " + gainText(ratio) + " " + metric + ".";
            case PARTIAL -> "Dá para trocar só o processador, mas o ganho é pequeno (" + gainText(ratio) + " " + metric + ").";
            case LIMITED -> candidates.isEmpty()
                    ? "Este já é um dos processadores mais rápidos do catálogo para esta placa-mãe."
                    : "Processadores mais rápidos para esta placa-mãe exigem trocar também " + limiter + ".";
        };
        StringBuilder explanation = new StringBuilder();
        if (level != Level.LIMITED) {
            explanation.append("Um processador como o ").append(best.name()).append(" usa o mesmo encaixe (").append(socket)
                    .append(") e funciona com a placa-mãe, a memória e o cooler que você já tem.")
                    .append(biosUpdate ? " Pode ser preciso atualizar a BIOS da placa-mãe antes da troca." : "")
                    .append(beyond(blockers, limiter));
        } else if (!candidates.isEmpty()) {
            explanation.append("Por exemplo, o ").append(candidates.getFirst().name()).append(" usa o mesmo encaixe (").append(socket)
                    .append("), mas ").append(lowerFirst(blockers.getFirst().explanation()));
        } else {
            explanation.append("Para um salto de desempenho no processador, será preciso trocar também a placa-mãe.");
        }
        timeline.socketSentence(socket).ifPresent(sentence -> explanation.append(' ').append(sentence));
        Cpu example = best != null && level != Level.LIMITED ? best : candidates.isEmpty() ? null : candidates.getFirst();
        String detail = example == null
                ? "Nenhum processador do catálogo para o encaixe " + socket + " é pelo menos "
                        + Math.round((MIN_UPGRADE - 1) * 100) + "% mais rápido."
                : RecommendationEngine.percentText(score.applyAsDouble(example), current,
                        gaming ? "desempenho em jogos" : "desempenho com vários programas");
        return Optional.of(new Aspect("cpu", "Processador", level, headline, explanation.toString(), detail,
                List.of(CPU, MOTHERBOARD)));
    }

    // ---------------------------------------------------------------------------------------------
    // Graphics card
    // ---------------------------------------------------------------------------------------------

    private Optional<Aspect> gpuPath(Catalog catalog, BuildParts parts, CompatibilityReport baseline) {
        PowerSupply psu = parts.powerSupply();
        PcCase pcCase = parts.pcCase();
        if (psu == null || pcCase == null) {
            return Optional.empty();
        }
        Gpu gpu = parts.gpu();
        double current = gpu == null ? 0 : orZero(PerformanceEstimator.gpuScore(gpu));
        if (gpu != null && current <= 0) {
            return Optional.empty();
        }
        Gpu best = null;
        List<CompatibilityFinding> blockers = List.of();
        List<Gpu> candidates = catalog.all(GPU, Gpu.class).stream()
                .filter(CandidatePolicy::gpu)
                .filter(candidate -> orZero(PerformanceEstimator.gpuScore(candidate)) > current * MIN_UPGRADE)
                .sorted(Comparator.comparingDouble((Gpu candidate) -> orZero(PerformanceEstimator.gpuScore(candidate)))
                        .reversed().thenComparing(Gpu::name))
                .toList();
        for (Gpu candidate : candidates) {
            List<CompatibilityFinding> problems = newProblems(baseline, parts.withGpu(candidate));
            if (problems.isEmpty()) {
                best = candidate;
                break;
            }
            if (blockers.isEmpty()) {
                blockers = problems;
            }
        }
        String limiter = limiterText(blockers);
        String powerDetail = (psu.wattage() == null ? "Consumo" : "Fonte de " + psu.wattage() + " W; consumo")
                + " estimado hoje de " + baseline.power().estimatedLoadWatts() + " W."
                + (pcCase.maxGpuLengthMm() == null ? "" : " O gabinete aceita placas de vídeo de até " + pcCase.maxGpuLengthMm() + " mm"
                + (gpu == null || gpu.lengthMm() == null ? "." : " (a atual tem " + gpu.lengthMm() + " mm)."));

        if (gpu == null) {
            if (best == null) {
                return Optional.of(new Aspect("gpu", "Placa de vídeo", Level.LIMITED,
                        "Para adicionar uma placa de vídeo, será preciso trocar " + limiter + ".",
                        "Hoje o vídeo sai do processador. Nenhuma placa de vídeo atual do catálogo cabe sem outra troca.",
                        powerDetail, List.of(GPU, POWER_SUPPLY, CASE)));
            }
            return Optional.of(new Aspect("gpu", "Placa de vídeo", Level.GOOD,
                    "Dá para adicionar uma placa de vídeo depois, sem trocar fonte nem gabinete.",
                    "Hoje o vídeo sai do processador. Uma placa como a " + chipName(best)
                            + " cabe no gabinete e a fonte dá conta dela." + beyond(blockers, limiter),
                    powerDetail, List.of(GPU, POWER_SUPPLY, CASE)));
        }
        double ratio = best == null ? 1 : orZero(PerformanceEstimator.gpuScore(best)) / current;
        Level level = ratio >= CLEAR_GPU_GAIN ? Level.GOOD : ratio >= SMALL_GAIN ? Level.PARTIAL : Level.LIMITED;
        String headline = switch (level) {
            case GOOD -> "Cabe uma placa de vídeo bem mais forte (" + gainText(ratio) + ") sem trocar fonte nem gabinete.";
            case PARTIAL -> "Cabe uma placa de vídeo um pouco mais forte (" + gainText(ratio) + ") sem trocar fonte nem gabinete.";
            case LIMITED -> "Para uma placa de vídeo mais forte, será preciso trocar " + limiter + ".";
        };
        String explanation = level == Level.LIMITED
                ? "A placa de vídeo atual já usa quase toda a folga de " + limiter + "."
                : "Por exemplo, uma " + chipName(best) + " cabe no gabinete e a fonte dá conta dela." + beyond(blockers, limiter);
        String detail = (level == Level.LIMITED ? "" : RecommendationEngine.percentText(
                orZero(PerformanceEstimator.gpuScore(best)), current, "desempenho gráfico") + " ") + powerDetail;
        return Optional.of(new Aspect("gpu", "Placa de vídeo", level, headline, explanation, detail,
                List.of(GPU, POWER_SUPPLY, CASE)));
    }

    /** Cards are named by their chip ("GeForce RTX 5070"): the many board-partner variants perform alike. */
    private static String chipName(Gpu gpu) {
        return gpu.chipset() != null ? gpu.chipset() : gpu.name();
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static String beyond(List<CompatibilityFinding> blockers, String limiter) {
        return blockers.isEmpty() ? "" : " Acima disso, o limite passa a ser " + limiter + ".";
    }

    /** Which parts would have to go, from the problems the strongest candidate ran into. */
    private static String limiterText(List<CompatibilityFinding> blockers) {
        Set<ComponentCategory> parts = EnumSet.noneOf(ComponentCategory.class);
        for (CompatibilityFinding finding : blockers) {
            finding.involves().stream()
                    .filter(category -> category == POWER_SUPPLY || category == CASE || category == MOTHERBOARD || category == CPU_COOLER)
                    .forEach(parts::add);
        }
        List<String> names = parts.stream().map(category -> switch (category) {
            case POWER_SUPPLY -> "a fonte";
            case CASE -> "o gabinete";
            case CPU_COOLER -> "o cooler";
            default -> "a placa-mãe";
        }).toList();
        return names.isEmpty() ? "outras peças" : joinPt(names);
    }

    // ---------------------------------------------------------------------------------------------
    // Memory
    // ---------------------------------------------------------------------------------------------

    private static Optional<Aspect> memoryRoom(BuildParts parts) {
        Motherboard board = parts.motherboard();
        Memory memory = parts.memory();
        if (memory == null || board.memorySlots() == null || memory.modules() == null || memory.totalCapacityGb() == null) {
            return Optional.empty();
        }
        int slots = board.memorySlots();
        int free = Math.max(0, slots - memory.modules());
        Integer max = board.maxMemoryGb();
        String limit = max == null ? "" : " A placa-mãe aceita até " + max + " GB.";
        String detail = "Slots: " + slots + " na placa-mãe, " + memory.modules() + " em uso. Kit atual: " + memory.totalCapacityGb() + " GB.";
        if (free > 0) {
            int sameKits = slots / memory.modules();
            int reachable = memory.totalCapacityGb() * sameKits;
            if (max != null) {
                reachable = Math.min(reachable, max);
            }
            return Optional.of(new Aspect("memory-room", "Quantidade de memória", Level.GOOD,
                    "Dá para aumentar a memória só adicionando pentes.",
                    "Sobram " + free + " de " + slots + " slots. Repetindo o mesmo kit, você chega a " + reachable
                            + " GB. Prefira o mesmo modelo: kits diferentes podem não funcionar na velocidade anunciada." + limit,
                    detail, List.of(MEMORY, MOTHERBOARD)));
        }
        return Optional.of(new Aspect("memory-room", "Quantidade de memória", Level.PARTIAL,
                "Para ter mais memória, será preciso trocar o kit.",
                "Os " + slots + " slots da placa-mãe já estão ocupados." + limit,
                detail, List.of(MEMORY, MOTHERBOARD)));
    }

    private static Optional<Aspect> memoryGeneration(BuildParts parts, PlatformTimeline timeline) {
        String type = parts.motherboard().ramType();
        if (type == null || timeline.recentMemoryTypes().isEmpty()) {
            return Optional.empty();
        }
        String recent = joinPt(List.copyOf(timeline.recentMemoryTypes()));
        String years = timeline.recentYearsText();
        if (timeline.recentMemoryTypes().contains(type)) {
            return Optional.of(new Aspect("memory-generation", "Geração da memória", Level.GOOD,
                    "A memória " + type + " é a usada pelos processadores mais recentes.",
                    "Os processadores lançados em " + years + " no catálogo usam " + recent
                            + ". Numa troca futura de plataforma, a memória pode ser reaproveitada.",
                    "Memória da placa-mãe: " + type + ".", List.of(MEMORY, MOTHERBOARD)));
        }
        return Optional.of(new Aspect("memory-generation", "Geração da memória", Level.LIMITED,
                "A memória " + type + " não é usada pelos processadores mais recentes.",
                "Os processadores lançados em " + years + " no catálogo usam só " + recent
                        + ". Enquanto você ficar nesta plataforma, ela funciona normalmente; se um dia trocar processador"
                        + " e placa-mãe por uma plataforma nova, a memória também terá de ser trocada.",
                "Memória da placa-mãe: " + type + ".", List.of(MEMORY, MOTHERBOARD)));
    }

    // ---------------------------------------------------------------------------------------------
    // Storage
    // ---------------------------------------------------------------------------------------------

    private static Optional<Aspect> storageRoom(BuildParts parts) {
        Motherboard board = parts.motherboard();
        if (board.m2Slots() == null) {
            return Optional.empty();
        }
        long nvmeSlots = board.m2Slots().stream().filter(Motherboard.M2Slot::acceptsNvme).count();
        long m2Used = parts.storage().stream().filter(Storage::isM2).count();
        long freeM2 = Math.max(0, nvmeSlots - m2Used);
        Integer sataPorts = board.sataPorts();
        long freeSata = sataPorts == null ? -1 : Math.max(0, sataPorts - parts.storage().stream().filter(Storage::usesSataPort).count());
        String sata = freeSata < 0 ? "" : freeSata == 0 ? " Não sobram portas SATA." : " Sobram também " + freeSata + " portas SATA para SSDs ou HDs de 2,5\"/3,5\".";
        // Some records list one physical slot once per supported length, so the free count is only stated when it is 1.
        String detail = "Encaixes M.2 para SSD segundo os dados: " + nvmeSlots + " (" + board.driveM2Slots().stream().map(Motherboard.M2Slot::interfaceName).filter(Objects::nonNull)
                .collect(Collectors.joining(", ")) + ")" + (sataPorts == null ? "." : "; SATA: " + sataPorts + " portas.");
        if (freeM2 > 0) {
            return Optional.of(new Aspect("storage", "Armazenamento", Level.GOOD,
                    freeM2 == 1 ? "Há 1 encaixe M.2 livre para outro SSD rápido." : "Há encaixes M.2 livres para outros SSDs rápidos.",
                    "Dá para adicionar espaço sem trocar o SSD atual." + sata, detail, List.of(STORAGE, MOTHERBOARD)));
        }
        if (freeSata > 0) {
            return Optional.of(new Aspect("storage", "Armazenamento", Level.PARTIAL,
                    "Os encaixes M.2 estão ocupados, mas ainda dá para adicionar SSDs ou HDs SATA.",
                    "SSDs SATA são mais lentos que os M.2, mas bons para guardar arquivos e jogos." + sata,
                    detail, List.of(STORAGE, MOTHERBOARD)));
        }
        return Optional.of(new Aspect("storage", "Armazenamento", Level.LIMITED,
                "Não sobram encaixes para outro SSD; para ter mais espaço, será preciso trocar por um de maior capacidade.",
                "Os encaixes M.2 estão ocupados." + sata, detail, List.of(STORAGE, MOTHERBOARD)));
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static boolean isBiosCaveat(CompatibilityFinding finding) {
        return "cpu-board.generation".equals(finding.ruleId()) && finding.status() == CompatibilityStatus.WARNING;
    }

    /** Problems the candidate build has that the current build does not (new rules failing, or failing harder). */
    private List<CompatibilityFinding> newProblems(CompatibilityReport baseline, BuildParts candidate) {
        Map<String, CompatibilityStatus> before = new HashMap<>();
        for (CompatibilityFinding finding : baseline.findings()) {
            before.merge(finding.ruleId(), finding.status(), CompatibilityStatus::worst);
        }
        return compatibility.check(candidate).findings().stream()
                .filter(finding -> finding.status() != CompatibilityStatus.OK)
                .filter(finding -> finding.status().ordinal() > before.getOrDefault(finding.ruleId(), CompatibilityStatus.OK).ordinal())
                .toList();
    }

    private static String summary(List<Aspect> aspects) {
        List<String> limited = aspects.stream().filter(a -> a.level() == Level.LIMITED).map(a -> a.title().toLowerCase(PT_BR)).toList();
        long good = aspects.stream().filter(a -> a.level() == Level.GOOD).count();
        if (limited.isEmpty()) {
            return "Boa base para evoluir: dá para melhorar as peças aos poucos, sem recomeçar do zero.";
        }
        if (good == 0) {
            return "Pouco espaço para evoluir sem trocar várias peças de uma vez.";
        }
        return "Dá para evoluir aos poucos, com limites em: " + joinPt(limited) + ".";
    }

    /** "cerca de 45% a mais" or, for big jumps, "cerca de 2× o desempenho". */
    static String gainText(double ratio) {
        if (ratio >= 1.8) {
            double rounded = Math.round(ratio * 2) / 2.0;
            String number = rounded == Math.floor(rounded) ? String.valueOf((long) rounded) : String.format(PT_BR, "%.1f", rounded);
            return "cerca de " + number + "× o desempenho";
        }
        long percent = Math.max(5, Math.round((ratio - 1) * 20) * 5);
        return "cerca de " + percent + "% a mais de desempenho";
    }

    private static String joinPt(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " e " + items.getLast();
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }

    /** Release-year facts about desktop platforms, read from the catalog. */
    record PlatformTimeline(Map<String, Integer> newestYearBySocket, Integer newestYear, Set<String> recentMemoryTypes) {

        static PlatformTimeline of(Catalog catalog) {
            Map<String, Integer> bySocket = new HashMap<>();
            Integer newest = null;
            List<Cpu> dated = catalog.all(CPU, Cpu.class).stream()
                    .filter(CandidatePolicy::consumerCpu)
                    .filter(cpu -> cpu.info().releaseYear() != null)
                    .toList();
            for (Cpu cpu : dated) {
                int year = cpu.info().releaseYear();
                String socket = Hardware.normalizeSocket(cpu.socket());
                if (socket != null) {
                    bySocket.merge(socket, year, Math::max);
                }
                newest = newest == null ? year : Math.max(newest, year);
            }
            Set<String> recentTypes = new TreeSet<>();
            if (newest != null) {
                int from = newest - 1;
                dated.stream().filter(cpu -> cpu.info().releaseYear() >= from).forEach(cpu -> recentTypes.addAll(cpu.memoryTypes()));
            }
            return new PlatformTimeline(bySocket, newest, recentTypes);
        }

        String recentYearsText() {
            return (newestYear - 1) + " e " + newestYear;
        }

        /** Whether this socket still gets new processors, stated as a catalog fact. */
        Optional<String> socketSentence(String socket) {
            Integer year = socket == null ? null : newestYearBySocket.get(socket);
            if (year == null || newestYear == null) {
                return Optional.empty();
            }
            if (year >= newestYear) {
                return Optional.of("O encaixe " + socket + " está entre os que recebem os processadores mais recentes do catálogo (" + year + ").");
            }
            return Optional.of("O catálogo não tem processadores para o encaixe " + socket + " lançados depois de " + year
                    + "; os mais recentes (" + newestYear + ") usam outros encaixes.");
        }
    }
}
