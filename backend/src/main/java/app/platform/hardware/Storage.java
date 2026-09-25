package app.platform.hardware;

public record Storage(
        ComponentInfo info,
        String storageType,
        Integer capacityGb,
        String formFactor,
        String interfaceName,
        Boolean nvme) implements HardwareComponent {

    public boolean isM2() {
        return formFactor != null && formFactor.startsWith("M.2");
    }

    /** Length code of an M.2 drive, e.g. "2280"; {@code null} for other form factors. */
    public String m2Size() {
        return isM2() && formFactor.contains("-") ? formFactor.substring(formFactor.indexOf('-') + 1) : null;
    }

    public boolean usesSataPort() {
        return !isM2() && interfaceName != null && interfaceName.startsWith("SATA");
    }

    public boolean isSsd() {
        return "SSD".equals(storageType);
    }
}
