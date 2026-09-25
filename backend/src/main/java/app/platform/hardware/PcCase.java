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
        supportedMotherboardFormFactors = supportedMotherboardFormFactors == null ? Set.of() : Set.copyOf(supportedMotherboardFormFactors);
        supportedPsuFormFactors = supportedPsuFormFactors == null ? Set.of() : Set.copyOf(supportedPsuFormFactors);
    }
}
