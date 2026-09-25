package app.platform.hardware;

import java.util.UUID;

/**
 * A hardware part in the platform's own domain model. Nothing outside the source adapters knows
 * the shape of external records; every engine works with these types.
 *
 * <p>Numeric and boolean fields are nullable: {@code null} means "unknown", never zero or false.
 */
public sealed interface HardwareComponent
        permits Cpu, Gpu, Motherboard, Memory, Storage, PowerSupply, PcCase, CpuCooler {

    ComponentInfo info();

    default UUID id() {
        return info().id();
    }

    default String name() {
        return info().name();
    }

    default ComponentCategory category() {
        return info().category();
    }
}
