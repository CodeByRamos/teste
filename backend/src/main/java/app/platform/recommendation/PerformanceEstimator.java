package app.platform.recommendation;

import app.platform.hardware.Cpu;
import app.platform.hardware.Gpu;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Relative performance estimates derived only from published specifications (v1).
 *
 * <p>These are NOT benchmarks. They rank parts within a generation reasonably but can misjudge parts
 * from different vendors or architectures. They are shown to users only as "estimativa" and will be
 * replaced by a curated, source-cited benchmark table.
 */
public final class PerformanceEstimator {

    public static final String DISCLAIMER =
            "Estimativa baseada nas especificações técnicas (núcleos, frequência, cache e arquitetura), não em testes reais de desempenho.";

    private static final double SMT_GAIN = 1.3;
    private static final double EFFICIENCY_CORE_WEIGHT = 0.55;

    private PerformanceEstimator() {
    }

    /**
     * Architecture calibration (heuristic v1). Vendors count shader cores differently: from GeForce RTX 30 on,
     * NVIDIA counts each SM's dual-issue FP32 units as separate cores, which roughly doubles the count without doubling
     * game performance; AMD and Intel generations differ as well. These factors bring core × clock onto a comparable
     * scale. They are coarse and will be replaced by a curated, source-cited benchmark table.
     */
    private static final List<Map.Entry<Pattern, Double>> ARCHITECTURE_FACTORS = List.of(
            Map.entry(Pattern.compile("RTX (30|40|50)\\d0"), 0.6),
            Map.entry(Pattern.compile("RTX 20\\d0|GTX 16\\d0"), 1.0),
            Map.entry(Pattern.compile("GTX 10\\d0|Titan X"), 0.9),
            Map.entry(Pattern.compile("GTX (9|7)\\d0"), 0.8),
            Map.entry(Pattern.compile("RX 9\\d{3}"), 1.0),
            Map.entry(Pattern.compile("RX 7\\d{3}"), 0.85),
            Map.entry(Pattern.compile("RX (5|6)\\d{3}"), 0.9),
            Map.entry(Pattern.compile("RX (4|5)\\d0|Vega|R9 "), 0.7),
            Map.entry(Pattern.compile("Arc B"), 0.65),
            Map.entry(Pattern.compile("Arc A"), 0.4));
    private static final double DEFAULT_ARCHITECTURE_FACTOR = 0.8;

    /** Raw shader throughput: cores × boost clock (GHz), without architecture calibration. */
    public static Double gpuThroughput(Gpu gpu) {
        if (gpu.coreCount() == null || gpu.boostClockMhz() == null) {
            return null;
        }
        return gpu.coreCount() * gpu.boostClockMhz() / 1000.0;
    }

    /** Relative graphics performance estimate: throughput calibrated by architecture. */
    public static Double gpuScore(Gpu gpu) {
        Double throughput = gpuThroughput(gpu);
        if (throughput == null) {
            return null;
        }
        String chipset = gpu.chipset() == null ? "" : gpu.chipset();
        double factor = ARCHITECTURE_FACTORS.stream()
                .filter(entry -> entry.getKey().matcher(chipset).find())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(DEFAULT_ARCHITECTURE_FACTOR);
        return throughput * factor;
    }

    /** Throughput proxy for work that uses every core: compiling, exporting video, containers. */
    public static Double cpuMultiThreadScore(Cpu cpu) {
        Integer pCores = cpu.effectivePerformanceCores();
        if (pCores == null || cpu.boostClockGhz() == null) {
            return null;
        }
        boolean smt = cpu.threads() != null && cpu.cores() != null && cpu.threads() > cpu.cores();
        double performance = pCores * cpu.boostClockGhz() * (smt ? SMT_GAIN : 1.0);
        double efficiencyClock = cpu.efficiencyBoostClockGhz() == null || cpu.efficiencyBoostClockGhz() == 0
                ? cpu.boostClockGhz() * 0.75
                : cpu.efficiencyBoostClockGhz();
        return performance + cpu.effectiveEfficiencyCores() * efficiencyClock * EFFICIENCY_CORE_WEIGHT;
    }

    /**
     * Games lean on a few fast cores and benefit from large L3 cache; beyond eight cores
     * the gain is small.
     */
    public static Double cpuGamingScore(Cpu cpu) {
        Integer pCores = cpu.effectivePerformanceCores();
        if (pCores == null || cpu.boostClockGhz() == null) {
            return null;
        }
        double coreFactor = Math.pow(Math.min(pCores, 8) / 8.0, 0.35);
        double cache = cpu.l3CacheMb() == null ? 16 : cpu.l3CacheMb();
        double cacheFactor = 1 + 0.35 * Math.min(cache, 96) / 96.0;
        return cpu.boostClockGhz() * coreFactor * cacheFactor;
    }
}
