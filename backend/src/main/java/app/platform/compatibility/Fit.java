package app.platform.compatibility;

import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.Gpu;
import app.platform.hardware.Hardware;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.Storage;

/**
 * Pairwise physical/electrical checks shared by the compatibility rules (which explain results)
 * and the recommendation engine (which only picks parts that pass).
 */
public final class Fit {

    /** Clearance below which a part is considered a tight fit. */
    public static final int TIGHT_CLEARANCE_MM = 10;

    public enum Verdict {
        YES, NO, UNKNOWN;

        static Verdict of(boolean value) {
            return value ? YES : NO;
        }
    }

    private Fit() {
    }

    public static Verdict socket(Cpu cpu, Motherboard board) {
        String cpuSocket = Hardware.normalizeSocket(cpu.socket());
        String boardSocket = Hardware.normalizeSocket(board.socket());
        if (cpuSocket == null || boardSocket == null) {
            return Verdict.UNKNOWN;
        }
        return Verdict.of(cpuSocket.equalsIgnoreCase(boardSocket));
    }

    /**
     * Processor generation vs. chipset: NO when the board cannot run the CPU, UNKNOWN when it may need
     * a BIOS update first, YES when no generation concern is known.
     */
    public static Verdict generationSupport(Cpu cpu, Motherboard board) {
        return PlatformSupport.evaluate(cpu, board)
                .map(outcome -> outcome.status() == CompatibilityStatus.INCOMPATIBLE ? Verdict.NO : Verdict.UNKNOWN)
                .orElse(Verdict.YES);
    }

    /** Whether the CPU's memory controller supports the memory generation the board uses. */
    public static Verdict cpuSupportsBoardMemory(Cpu cpu, Motherboard board) {
        if (cpu.memoryTypes().isEmpty() || board.ramType() == null) {
            return Verdict.UNKNOWN;
        }
        return Verdict.of(cpu.memoryTypes().contains(board.ramType()));
    }

    public static Verdict memoryType(Memory memory, Motherboard board) {
        if (memory.ramType() == null || board.ramType() == null) {
            return Verdict.UNKNOWN;
        }
        return Verdict.of(memory.ramType().equals(board.ramType()));
    }

    public static Verdict memoryIsDesktopModule(Memory memory) {
        Boolean laptop = memory.isLaptopModule();
        return laptop == null ? Verdict.UNKNOWN : Verdict.of(!laptop);
    }

    public static Verdict memorySlots(Memory memory, Motherboard board) {
        if (memory.modules() == null || board.memorySlots() == null) {
            return Verdict.UNKNOWN;
        }
        return Verdict.of(memory.modules() <= board.memorySlots());
    }

    public static Verdict memoryCapacity(Memory memory, Motherboard board) {
        if (memory.totalCapacityGb() == null || board.maxMemoryGb() == null) {
            return Verdict.UNKNOWN;
        }
        return Verdict.of(memory.totalCapacityGb() <= board.maxMemoryGb());
    }

    public static Verdict coolerSocket(CpuCooler cooler, Cpu cpu) {
        String cpuSocket = Hardware.normalizeSocket(cpu.socket());
        if (cpuSocket == null || cooler.sockets().isEmpty()) {
            return Verdict.UNKNOWN;
        }
        return Verdict.of(cooler.sockets().stream().map(Hardware::normalizeSocket).anyMatch(cpuSocket::equalsIgnoreCase));
    }

    public static Verdict motherboardInCase(Motherboard board, PcCase pcCase) {
        if (board.formFactor() == null || pcCase.supportedMotherboardFormFactors().isEmpty()) {
            return Verdict.UNKNOWN;
        }
        return Verdict.of(pcCase.supportedMotherboardFormFactors().contains(board.formFactor()));
    }

    public static Verdict gpuLengthInCase(Gpu gpu, PcCase pcCase) {
        if (gpu.lengthMm() == null || pcCase.maxGpuLengthMm() == null) {
            return Verdict.UNKNOWN;
        }
        return Verdict.of(gpu.lengthMm() <= pcCase.maxGpuLengthMm());
    }

    /** Air coolers only; liquid coolers depend on radiator mounts, which the data does not describe. */
    public static Verdict coolerHeightInCase(CpuCooler cooler, PcCase pcCase) {
        if (!cooler.isAirCooler() || cooler.heightMm() == null || pcCase.maxCoolerHeightMm() == null) {
            return Verdict.UNKNOWN;
        }
        return Verdict.of(cooler.heightMm() <= pcCase.maxCoolerHeightMm());
    }

    public static Verdict gpuSlot(Motherboard board) {
        Boolean slot = board.hasFullLengthPcieSlot();
        return slot == null ? Verdict.UNKNOWN : Verdict.of(slot);
    }

    public static Verdict psuFormFactorInCase(PowerSupply psu, PcCase pcCase) {
        if (psu.formFactor() == null) {
            return Verdict.UNKNOWN;
        }
        if (!pcCase.supportedPsuFormFactors().isEmpty()) {
            return Verdict.of(pcCase.supportedPsuFormFactors().contains(psu.formFactor()));
        }
        // Standard ATX and Micro ATX towers take a standard ATX power supply.
        String caseForm = pcCase.formFactor();
        if ("ATX".equals(psu.formFactor()) && caseForm != null && caseForm.endsWith("Tower") && !caseForm.startsWith("Mini ITX")) {
            return Verdict.YES;
        }
        return Verdict.UNKNOWN;
    }

    public static Verdict psuLengthInCase(PowerSupply psu, PcCase pcCase) {
        if (psu.lengthMm() == null || pcCase.maxPsuLengthMm() == null) {
            return Verdict.UNKNOWN;
        }
        return Verdict.of(psu.lengthMm() <= pcCase.maxPsuLengthMm());
    }

    /** Whether the PSU can feed the GPU's auxiliary power plugs natively (no adapters). */
    public static Verdict psuConnectorsForGpu(PowerSupply psu, Gpu gpu) {
        Gpu.PowerConnectors needs = gpu.powerConnectors();
        if (needs == null || psu.pcieEightPinConnectors() == null) {
            return Verdict.UNKNOWN;
        }
        if (needs.highPower16Pin() > 0) {
            Integer native16 = psu.highPower16PinConnectors();
            if (native16 == null) {
                return Verdict.UNKNOWN;
            }
            return Verdict.of(native16 >= needs.highPower16Pin());
        }
        return Verdict.of(psu.pcieEightPinConnectors() >= needs.totalPcieCables());
    }

    /** Whether one M.2 drive can go into at least one of the board's M.2 slots. */
    public static Verdict m2DriveOnBoard(Storage drive, Motherboard board) {
        if (board.m2Slots() == null) {
            return Verdict.UNKNOWN;
        }
        String size = drive.m2Size();
        boolean nvme = Boolean.TRUE.equals(drive.nvme()) || (drive.interfaceName() != null && drive.interfaceName().contains("PCIe"));
        return Verdict.of(board.m2Slots().stream()
                .anyMatch(slot -> (size == null || slot.sizes().isEmpty() || slot.sizes().contains(size))
                        && (nvme ? slot.acceptsNvme() : slot.acceptsSata())));
    }
}
