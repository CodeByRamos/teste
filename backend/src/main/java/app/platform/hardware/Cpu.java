package app.platform.hardware;

import java.util.Set;

public record Cpu(
        ComponentInfo info,
        String socket,
        String microarchitecture,
        Integer cores,
        Integer performanceCores,
        Integer efficiencyCores,
        Integer threads,
        Double baseClockGhz,
        Double boostClockGhz,
        Double efficiencyBoostClockGhz,
        Double l3CacheMb,
        Integer tdpWatts,
        Integer maxPowerWatts,
        Boolean integratedGraphics,
        String integratedGraphicsModel,
        Set<String> memoryTypes,
        Integer maxMemoryGb,
        Boolean includesCooler) implements HardwareComponent {

    public Cpu {
        memoryTypes = Hardware.sortedSet(memoryTypes);
    }

    /**
     * Cores that run at the headline clock. Some records report 0/0 for hybrid split on
     * non-hybrid designs; in that case every core counts as a performance core.
     */
    public Integer effectivePerformanceCores() {
        if (performanceCores != null && performanceCores > 0) {
            return performanceCores;
        }
        return cores;
    }

    public int effectiveEfficiencyCores() {
        return performanceCores != null && performanceCores > 0 && efficiencyCores != null ? efficiencyCores : 0;
    }

    /** Sustained power the platform should budget for: the higher of rated TDP and package power limit. */
    public Integer powerBudgetWatts() {
        if (tdpWatts == null) {
            return maxPowerWatts;
        }
        return maxPowerWatts == null ? tdpWatts : Math.max(tdpWatts, maxPowerWatts);
    }
}
