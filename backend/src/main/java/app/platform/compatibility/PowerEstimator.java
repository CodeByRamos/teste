package app.platform.compatibility;

import app.platform.hardware.Cpu;
import app.platform.hardware.Gpu;

/**
 * Power budget heuristic (v1).
 *
 * <p>Load = CPU power limit (higher of TDP and package power) + GPU board power + a fixed allowance for
 * motherboard, memory, fans and one drive, plus a small allowance per additional drive. The recommended
 * PSU adds 40% headroom — modern GPUs have short power spikes well above their rated board power —
 * rounded up to the next 50 W, never below 450 W.
 */
public final class PowerEstimator {

    static final int PLATFORM_ALLOWANCE_WATTS = 50;
    static final int EXTRA_DRIVE_WATTS = 7;
    static final double HEADROOM = 1.4;
    static final int MINIMUM_RECOMMENDED_WATTS = 450;

    private PowerEstimator() {
    }

    public static PowerEstimate estimate(BuildParts parts) {
        return estimate(parts.cpu(), parts.gpu(), parts.storage().size());
    }

    public static PowerEstimate estimate(Cpu cpu, Gpu gpu, int drives) {
        boolean complete = true;
        int load = PLATFORM_ALLOWANCE_WATTS + Math.max(0, drives - 1) * EXTRA_DRIVE_WATTS;
        if (cpu != null) {
            Integer cpuPower = cpu.powerBudgetWatts();
            if (cpuPower == null) {
                complete = false;
            } else {
                load += cpuPower;
            }
        }
        if (gpu != null) {
            if (gpu.tdpWatts() == null) {
                complete = false;
            } else {
                load += gpu.tdpWatts();
            }
        }
        int recommended = Math.max(MINIMUM_RECOMMENDED_WATTS, roundUpTo50((int) Math.ceil(load * HEADROOM)));
        return new PowerEstimate(load, recommended, complete);
    }

    private static int roundUpTo50(int watts) {
        return ((watts + 49) / 50) * 50;
    }
}
