package app.platform.recommendation;

import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.Gpu;
import app.platform.hardware.Hardware;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.Storage;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which catalog parts the engine may suggest for a new build (v1 product policy).
 *
 * <p>The catalog covers two decades of hardware. New builds only use current desktop platforms,
 * mainstream form factors, and records whose fields needed for compatibility checks are present.
 * Parts the person already owns bypass this policy — they are checked, not chosen.
 */
final class CandidatePolicy {

    static final Set<String> CURRENT_SOCKETS = Set.of("AM4", "AM5", "LGA 1700", "LGA 1851");
    private static final Set<String> AM4_CURRENT_ARCHITECTURES = Set.of("Zen 2", "Zen 3");
    private static final Pattern NON_CONSUMER_CPU = Pattern.compile("EPYC|Xeon|\\bPRO\\b");
    private static final Pattern CURRENT_GPU_FAMILY = Pattern.compile(
            "^(GeForce RTX (30|40|50)\\d0.*|Radeon RX (6|7|9)\\d{2}0.*|Arc [AB]\\d{3}.*)$");
    private static final Set<String> MAINSTREAM_BOARD_SIZES = Set.of("ATX", "Micro ATX");
    private static final Set<String> MAINSTREAM_CASES = Set.of(
            "ATX Mid Tower", "ATX Full Tower", "Micro ATX Mid Tower", "Micro ATX Mini Tower");
    private static final Set<String> ACCEPTED_EFFICIENCY = Set.of(
            "80+ Bronze", "80+ Silver", "80+ Gold", "80+ Platinum", "80+ Titanium");

    private CandidatePolicy() {
    }

    static boolean cpu(Cpu cpu) {
        String socket = Hardware.normalizeSocket(cpu.socket());
        return socket != null && in(CURRENT_SOCKETS, socket)
                && !NON_CONSUMER_CPU.matcher(cpu.name()).find()
                && (!"AM4".equals(socket) || in(AM4_CURRENT_ARCHITECTURES, cpu.microarchitecture()))
                && cpu.cores() != null && cpu.threads() != null && cpu.boostClockGhz() != null
                && cpu.powerBudgetWatts() != null && !cpu.memoryTypes().isEmpty()
                && cpu.integratedGraphics() != null && cpu.includesCooler() != null;
    }

    static boolean gpu(Gpu gpu) {
        return gpu.chipset() != null && CURRENT_GPU_FAMILY.matcher(gpu.chipset()).matches()
                && gpu.coreCount() != null && gpu.boostClockMhz() != null && gpu.vramGb() != null
                && gpu.tdpWatts() != null && gpu.lengthMm() != null && gpu.powerConnectors() != null;
    }

    static boolean motherboard(Motherboard board) {
        String socket = Hardware.normalizeSocket(board.socket());
        return socket != null && in(CURRENT_SOCKETS, socket)
                && in(MAINSTREAM_BOARD_SIZES, board.formFactor())
                && ("DDR4".equals(board.ramType()) || "DDR5".equals(board.ramType()))
                && board.memorySlots() != null && board.maxMemoryGb() != null
                && board.m2Slots() != null && board.m2Slots().stream().anyMatch(Motherboard.M2Slot::acceptsNvme)
                && Boolean.TRUE.equals(board.hasFullLengthPcieSlot())
                && board.chipset() != null;
    }

    static boolean memory(Memory memory) {
        if (memory.ramType() == null || memory.speedMts() == null || memory.modules() == null
                || memory.totalCapacityGb() == null || !Boolean.FALSE.equals(memory.isLaptopModule())
                || Boolean.TRUE.equals(memory.ecc()) || Boolean.TRUE.equals(memory.registered())) {
            return false;
        }
        // Mainstream speeds: faster kits need manual tuning or may not run on every board at rated speed.
        return switch (memory.ramType()) {
            case "DDR4" -> memory.speedMts() >= 3000 && memory.speedMts() <= 3600;
            case "DDR5" -> memory.speedMts() >= 4800 && memory.speedMts() <= 6400;
            default -> false;
        } && memory.modules() <= 2;
    }

    static boolean bootDrive(Storage storage) {
        return storage.isSsd() && Boolean.TRUE.equals(storage.nvme()) && "2280".equals(storage.m2Size())
                && storage.capacityGb() != null && storage.capacityGb() >= 250;
    }

    /** 2.5" SATA SSDs: the upgrade path for older boards without a free M.2 slot. */
    static boolean sataSsd(Storage storage) {
        return storage.isSsd() && storage.usesSataPort() && storage.capacityGb() != null && storage.capacityGb() >= 240;
    }

    static boolean powerSupply(PowerSupply psu) {
        return "ATX".equals(psu.formFactor()) && psu.wattage() != null
                && in(ACCEPTED_EFFICIENCY, psu.efficiencyRating())
                && psu.pcieEightPinConnectors() != null && psu.highPower16PinConnectors() != null;
    }

    static boolean pcCase(PcCase pcCase) {
        return in(MAINSTREAM_CASES, pcCase.formFactor())
                && pcCase.maxGpuLengthMm() != null
                && !pcCase.supportedMotherboardFormFactors().isEmpty()
                && !Boolean.TRUE.equals(pcCase.includesPowerSupply());
    }

    static boolean cooler(CpuCooler cooler) {
        return cooler.isAirCooler() && cooler.heightMm() != null && !cooler.sockets().isEmpty()
                && !Boolean.TRUE.equals(cooler.fanless());
    }

    /** Null-safe membership: immutable sets throw on contains(null). */
    private static boolean in(Set<String> values, String value) {
        return value != null && values.contains(value);
    }
}
