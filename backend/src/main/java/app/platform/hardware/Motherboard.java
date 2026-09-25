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

        public boolean acceptsNvme() {
            return interfaceName != null && interfaceName.contains("PCIe");
        }

        public boolean acceptsSata() {
            return interfaceName != null && interfaceName.contains("SATA");
        }
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
