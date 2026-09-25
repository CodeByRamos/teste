package app.platform.infra.pricing;

import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.Gpu;
import app.platform.hardware.HardwareComponent;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.Storage;
import app.platform.pricing.Offer;
import app.platform.pricing.PriceProvider;
import app.platform.recommendation.PerformanceEstimator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEVELOPMENT ONLY. Produces fictitious prices so the recommendation flow can be exercised before real
 * store integrations exist.
 *
 * <p>Prices are a deterministic function of specifications (roughly proportional to capability, with a small
 * per-model variation). They are NOT market prices. Every offer is marked {@link Offer.Kind#EXAMPLE}, has no
 * URL and no availability, and the UI must label it as fictitious.
 */
public final class ExamplePriceProvider implements PriceProvider {

    public static final String ID = "example";
    public static final String STORE_NAME = "Preço de exemplo (fictício)";

    private final Instant generatedAt = Instant.now();
    private final Map<UUID, List<Offer>> cache = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Offer> offersFor(HardwareComponent component) {
        return cache.computeIfAbsent(component.id(), id -> {
            Double base = basePrice(component);
            if (base == null) {
                return List.of();
            }
            double price = base * variation(id);
            // Store-like endings (…9,90) make the example look plausible; it remains fictitious.
            BigDecimal rounded = BigDecimal.valueOf(Math.max(49, Math.floor(price / 10) * 10 - 0.1)).setScale(2, RoundingMode.HALF_UP);
            return List.of(new Offer(id, ID, STORE_NAME, rounded, null, Offer.Availability.UNKNOWN, generatedAt, Offer.Kind.EXAMPLE));
        });
    }

    private static Double basePrice(HardwareComponent component) {
        return switch (component) {
            case Cpu cpu -> {
                Double multi = PerformanceEstimator.cpuMultiThreadScore(cpu);
                if (multi == null) {
                    yield null;
                }
                double cache = cpu.l3CacheMb() == null ? 16 : cpu.l3CacheMb();
                double price = 12 * Math.pow(multi, 1.2) + 5 * Math.min(cache, 128);
                yield "AM4".equals(cpu.socket()) ? price * 0.85 : price;
            }
            case Gpu gpu -> {
                Double score = PerformanceEstimator.gpuScore(gpu);
                if (score == null) {
                    yield null;
                }
                double price = 250 + 0.22 * score + 45 * (gpu.vramGb() == null ? 4 : gpu.vramGb());
                boolean previousGeneration = gpu.chipset() != null && gpu.chipset().matches(".*(RTX 30\\d0|RX 6\\d{3}).*");
                yield previousGeneration ? price * 0.85 : price;
            }
            case Motherboard board -> {
                String chipset = board.chipset() == null ? "" : board.chipset();
                double price = chipset.matches(".*\\b[XZ]\\d{3}.*") ? 1700 : chipset.matches(".*\\bB\\d{3}.*") ? 850 : 550;
                if (chipset.matches(".*\\d{3}E\\b.*")) {
                    price *= 1.25;
                }
                if ("Micro ATX".equals(board.formFactor())) {
                    price *= 0.92;
                }
                if (board.hasWifi()) {
                    price *= 1.12;
                }
                yield "DDR5".equals(board.ramType()) ? price * 1.08 : price;
            }
            case Memory memory -> {
                if (memory.totalCapacityGb() == null) {
                    yield null;
                }
                double perGb = "DDR5".equals(memory.ramType()) ? 6.0 : 4.5;
                double price = 80 + perGb * memory.totalCapacityGb();
                boolean fast = memory.speedMts() != null && memory.speedMts() >= ("DDR5".equals(memory.ramType()) ? 6000 : 3600);
                yield fast ? price * 1.1 : price;
            }
            case Storage storage -> {
                if (storage.capacityGb() == null) {
                    yield null;
                }
                String link = storage.interfaceName() == null ? "" : storage.interfaceName();
                if (!storage.isSsd()) {
                    yield 180 + 0.08 * storage.capacityGb();
                }
                if (Boolean.TRUE.equals(storage.nvme())) {
                    double price = 90 + 0.30 * storage.capacityGb();
                    yield link.contains("5.0") ? price * 1.45 : link.contains("4.0") ? price * 1.1 : price;
                }
                yield 80 + 0.26 * storage.capacityGb();
            }
            case PowerSupply psu -> {
                if (psu.wattage() == null) {
                    yield null;
                }
                double efficiency = switch (psu.efficiencyRating() == null ? "" : psu.efficiencyRating()) {
                    case "80+ Bronze" -> 1.0;
                    case "80+ Silver" -> 1.05;
                    case "80+ Gold" -> 1.25;
                    case "80+ Platinum" -> 1.5;
                    case "80+ Titanium" -> 1.8;
                    default -> 0.9;
                };
                double modular = "Full".equals(psu.modular()) ? 1.1 : "Semi-Modular".equals(psu.modular()) ? 1.05 : 1.0;
                yield 120 + 0.42 * psu.wattage() * efficiency * modular;
            }
            case PcCase pcCase -> 220 + 2.5 * (pcCase.volumeLiters() == null ? 40 : pcCase.volumeLiters())
                    + (Boolean.TRUE.equals(pcCase.transparentSidePanel()) ? 40 : 0);
            case CpuCooler cooler -> {
                if (Boolean.TRUE.equals(cooler.waterCooled())) {
                    yield 200 + 1.4 * (cooler.radiatorSizeMm() == null ? 240 : cooler.radiatorSizeMm());
                }
                double fans = cooler.fanCount() != null && cooler.fanCount() >= 2 ? 1.3 : 1.0;
                yield 60 + 0.9 * (cooler.heightMm() == null ? 120 : cooler.heightMm()) * fans;
            }
        };
    }

    /** Deterministic ±8% per model, so variants of the same product do not all cost the same. */
    private static double variation(UUID id) {
        long bits = id.getLeastSignificantBits() ^ id.getMostSignificantBits();
        return 0.92 + (Math.floorMod(bits, 1000) / 1000.0) * 0.16;
    }
}
