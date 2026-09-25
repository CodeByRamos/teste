package app.platform.compatibility;

import app.platform.hardware.ComponentCategory;
import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.Gpu;
import app.platform.hardware.HardwareComponent;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.Storage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The parts of one PC. Any field may be {@code null} while the build is incomplete.
 * A build has at most one part per category, except storage drives.
 */
public record BuildParts(
        Cpu cpu,
        Gpu gpu,
        Motherboard motherboard,
        Memory memory,
        List<Storage> storage,
        PowerSupply powerSupply,
        PcCase pcCase,
        CpuCooler cooler) {

    public BuildParts {
        storage = storage == null ? List.of() : List.copyOf(storage);
    }

    public static BuildParts empty() {
        return new BuildParts(null, null, null, null, List.of(), null, null, null);
    }

    /** Groups arbitrary components into a build. Rejects a second part in single-part categories. */
    public static BuildParts of(List<? extends HardwareComponent> components) {
        BuildParts parts = empty();
        for (HardwareComponent component : components) {
            parts = parts.with(component);
        }
        return parts;
    }

    public BuildParts with(HardwareComponent component) {
        return switch (component) {
            case Cpu c -> requireEmpty(cpu, c).withCpu(c);
            case Gpu g -> requireEmpty(gpu, g).withGpu(g);
            case Motherboard m -> requireEmpty(motherboard, m).withMotherboard(m);
            case Memory m -> requireEmpty(memory, m).withMemory(m);
            case Storage s -> {
                List<Storage> drives = new ArrayList<>(storage);
                drives.add(s);
                yield new BuildParts(cpu, gpu, motherboard, memory, drives, powerSupply, pcCase, cooler);
            }
            case PowerSupply p -> requireEmpty(powerSupply, p).withPowerSupply(p);
            case PcCase c -> requireEmpty(pcCase, c).withCase(c);
            case CpuCooler c -> requireEmpty(cooler, c).withCooler(c);
        };
    }

    public BuildParts withCpu(Cpu value) {
        return new BuildParts(value, gpu, motherboard, memory, storage, powerSupply, pcCase, cooler);
    }

    public BuildParts withGpu(Gpu value) {
        return new BuildParts(cpu, value, motherboard, memory, storage, powerSupply, pcCase, cooler);
    }

    public BuildParts withMotherboard(Motherboard value) {
        return new BuildParts(cpu, gpu, value, memory, storage, powerSupply, pcCase, cooler);
    }

    public BuildParts withMemory(Memory value) {
        return new BuildParts(cpu, gpu, motherboard, value, storage, powerSupply, pcCase, cooler);
    }

    public BuildParts withStorage(List<Storage> value) {
        return new BuildParts(cpu, gpu, motherboard, memory, value, powerSupply, pcCase, cooler);
    }

    public BuildParts withPowerSupply(PowerSupply value) {
        return new BuildParts(cpu, gpu, motherboard, memory, storage, value, pcCase, cooler);
    }

    public BuildParts withCase(PcCase value) {
        return new BuildParts(cpu, gpu, motherboard, memory, storage, powerSupply, value, cooler);
    }

    public BuildParts withCooler(CpuCooler value) {
        return new BuildParts(cpu, gpu, motherboard, memory, storage, powerSupply, pcCase, value);
    }

    /** Replaces the part of the same category (for storage, replaces all drives with this one). */
    public BuildParts replacing(HardwareComponent component) {
        return switch (component) {
            case Cpu c -> withCpu(c);
            case Gpu g -> withGpu(g);
            case Motherboard m -> withMotherboard(m);
            case Memory m -> withMemory(m);
            case Storage s -> withStorage(List.of(s));
            case PowerSupply p -> withPowerSupply(p);
            case PcCase c -> withCase(c);
            case CpuCooler c -> withCooler(c);
        };
    }

    public List<HardwareComponent> all() {
        return Stream.<HardwareComponent>concat(
                        Stream.of(cpu, gpu, motherboard, memory).filter(p -> p != null),
                        Stream.concat(storage.stream(), Stream.of(powerSupply, pcCase, cooler).filter(p -> p != null)))
                .toList();
    }

    public boolean has(ComponentCategory category) {
        return switch (category) {
            case CPU -> cpu != null;
            case GPU -> gpu != null;
            case MOTHERBOARD -> motherboard != null;
            case MEMORY -> memory != null;
            case STORAGE -> !storage.isEmpty();
            case POWER_SUPPLY -> powerSupply != null;
            case CASE -> pcCase != null;
            case CPU_COOLER -> cooler != null;
        };
    }

    private BuildParts requireEmpty(HardwareComponent existing, HardwareComponent incoming) {
        if (existing != null) {
            throw new IllegalArgumentException("Uma configuração só pode ter uma peça do tipo \""
                    + incoming.category().label().toLowerCase() + "\", mas recebemos " + existing.name() + " e " + incoming.name() + ".");
        }
        return this;
    }
}
