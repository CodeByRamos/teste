package app.platform.recommendation;

import java.util.List;

/**
 * Technical requirements derived from what the person wants to do.
 *
 * <p>Weights express how much each kind of performance matters for this person (0 = irrelevant).
 * Secondary uses count less than the main one.
 * "Minimum" values are hard requirements; "target" values are used when the budget allows.
 *
 * @param enoughPerformance performance tier (0–1) beyond which more speed brings no real benefit for these uses;
 *                          lets the engine leave budget unspent instead of buying unneeded power
 * @param reasons plain-language statements explaining how needs became requirements
 */
public record RequirementProfile(
        boolean needsDedicatedGpu,
        boolean gamingPriority,
        double gpuWeight,
        double cpuMultiThreadWeight,
        double cpuGamingWeight,
        int minCpuThreads,
        int minRamGb,
        int targetRamGb,
        int minStorageGb,
        int targetStorageGb,
        int minVramGb,
        double enoughPerformance,
        List<String> reasons) {

    public RequirementProfile {
        reasons = List.copyOf(reasons);
    }

    /** Games are the main use (or the only ranking available), so GPU/CPU balance for games matters most. */
    public boolean gamingFocused() {
        return gamingPriority;
    }
}
