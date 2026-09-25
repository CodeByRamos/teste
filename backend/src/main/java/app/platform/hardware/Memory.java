package app.platform.hardware;

/** A RAM kit (one or more identical modules sold together). */
public record Memory(
        ComponentInfo info,
        String ramType,
        String formFactor,
        Integer speedMts,
        Integer casLatency,
        Integer modules,
        Integer moduleCapacityGb,
        Integer totalCapacityGb,
        Boolean ecc,
        Boolean registered) implements HardwareComponent {

    /** Laptop-style modules (SO-DIMM) do not fit desktop motherboards. */
    public Boolean isLaptopModule() {
        return formFactor == null ? null : formFactor.contains("SO-DIMM");
    }
}
