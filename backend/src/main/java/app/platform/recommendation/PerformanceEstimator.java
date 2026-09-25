package app.platform.recommendation;

import app.platform.hardware.Cpu;
import app.platform.hardware.Gpu;

/**
 * Relative performance estimates derived only from published specifications (v1).
 *
 * <p>These are NOT benchmarks. They rank parts within a generation reasonably but can misjudge parts
 * from different vendors or architectures. They are shown to users only as "estimativa" and will be
 * replaced by a curated, source-cited benchmark table.
 */
public final class PerformanceEstimator {

    public static final String DISCLAIMER =
            "Estimativa baseada nas especificações técnicas (núcleos, frequência e cache), não em testes reais de desempenho.";

    private static final double SMT_GAIN = 1.3;
    private static final double EFFICIENCY_CORE_WEIGHT = 0.55;

    private PerformanceEstimator() {
    }

    /** Shader throughput proxy: cores × boost clock (GHz). */
    public static Double gpuScore(Gpu gpu) {
        if (gpu.coreCount() == null || gpu.boostClockMhz() == null) {
            return null;
        }
        return gpu.coreCount() * gpu.boostClockMhz() / 1000.0;
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
