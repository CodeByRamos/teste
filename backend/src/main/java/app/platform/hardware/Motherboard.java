package app.platform.hardware;

import java.util.List;
import java.util.Set;

public record Motherboard(
        ComponentInfo info,
        String socket,
        String chipset,
        String formFactor,
        String ramType,
        Integer memorySlots,
        Integer maxMemoryGb,
        List<M2Slot> m2Slots,
        Integer sataPorts,
        List<PcieSlot> pcieSlots,
        String wireless) implements HardwareComponent {

    public Motherboard {
        m2Slots = m2Slots == null ? null : List.copyOf(m2Slots);
        pcieSlots = pcieSlots == null ? null : List.copyOf(pcieSlots);
    }

    /** One M.2 slot. {@code sizes} are length codes such as "2280". */
    public record M2Slot(Set<String> sizes, String key, String interfaceName) {

        public M2Slot {
            sizes = Hardware.sortedSet(sizes);
        }

        /** "E"-key slots hold Wi-Fi/Bluetooth cards, not drives; "M" (and "B+M") slots take SSDs. */
        public boolean forDrives() {
            return key == null || key.contains("M");
        }

        /** Records spell the bus as "PCIe 4.0 x4", "PCIE 3.0 x4" or "Gen4". */
        public boolean acceptsNvme() {
            if (!forDrives() || interfaceName == null) {
                return false;
            }
            String bus = interfaceName.toUpperCase(java.util.Locale.ROOT);
            return bus.contains("PCIE") || bus.startsWith("GEN");
        }

        public boolean acceptsSata() {
            return forDrives() && interfaceName != null && interfaceName.toUpperCase(java.util.Locale.ROOT).contains("SATA");
        }
    }

    /** M.2 slots that take drives (Wi-Fi card slots excluded); {@code null} when the record does not list M.2 slots. */
    public List<M2Slot> driveM2Slots() {
        return m2Slots == null ? null : m2Slots.stream().filter(M2Slot::forDrives).toList();
    }

    public record PcieSlot(String generation, int quantity, int lanes) {
    }

    /** {@code null} when the record does not describe its expansion slots. */
    public Boolean hasFullLengthPcieSlot() {
        if (pcieSlots == null || pcieSlots.isEmpty()) {
            return null;
        }
        return pcieSlots.stream().anyMatch(slot -> slot.lanes() >= 16 && slot.quantity() > 0);
    }

    /** Highest PCIe generation among x16 slots, e.g. 4 for "4.0". */
    public Integer bestFullLengthPcieGeneration() {
        if (pcieSlots == null) {
            return null;
        }
        return pcieSlots.stream()
                .filter(slot -> slot.lanes() >= 16)
                .map(slot -> Hardware.parseGeneration(slot.generation()))
                .filter(gen -> gen != null)
                .max(Integer::compare)
                .orElse(null);
    }

    public boolean hasWifi() {
        return wireless != null && !wireless.isBlank() && !wireless.equalsIgnoreCase("None");
    }
}
