package app.platform.hardware;

public record PowerSupply(
        ComponentInfo info,
        Integer wattage,
        String formFactor,
        String efficiencyRating,
        String modular,
        Integer lengthMm,
        Integer pcieEightPinConnectors,
        Integer highPower16PinConnectors,
        Integer cpuEightPinConnectors) implements HardwareComponent {
}
