package app.platform.hardware;

import java.util.Set;

public record PcCase(
        ComponentInfo info,
        String formFactor,
        Set<String> supportedMotherboardFormFactors,
        Integer maxGpuLengthMm,
        Integer maxCoolerHeightMm,
        Integer maxPsuLengthMm,
        Set<String> supportedPsuFormFactors,
        Boolean includesPowerSupply,
        Integer expansionSlots,
        Double volumeLiters,
        Boolean transparentSidePanel) implements HardwareComponent {

    public PcCase {
        supportedMotherboardFormFactors = Hardware.sortedSet(supportedMotherboardFormFactors);
        supportedPsuFormFactors = Hardware.sortedSet(supportedPsuFormFactors);
    }
}
