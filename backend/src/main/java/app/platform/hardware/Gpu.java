package app.platform.hardware;

public record Gpu(
        ComponentInfo info,
        String chipsetManufacturer,
        String chipset,
        Integer vramGb,
        String memoryType,
        Integer coreCount,
        Integer boostClockMhz,
        Integer tdpWatts,
        Integer lengthMm,
        Double slotWidth,
        Integer pcieGeneration,
        Integer pcieLanes,
        PowerConnectors powerConnectors) implements HardwareComponent {

    /** Auxiliary power plugs the card needs from the power supply. {@code null} on the GPU means unknown. */
    public record PowerConnectors(int sixPin, int eightPin, int highPower16Pin) {

        public int totalPcieCables() {
            return sixPin + eightPin;
        }
    }
}
