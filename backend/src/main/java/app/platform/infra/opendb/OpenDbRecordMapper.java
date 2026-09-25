package app.platform.infra.opendb;

import app.platform.hardware.ComponentCategory;
import app.platform.hardware.ComponentInfo;
import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.DataQuality;
import app.platform.hardware.Gpu;
import app.platform.hardware.Hardware;
import app.platform.hardware.HardwareComponent;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.SourceRef;
import app.platform.hardware.Storage;
import app.platform.hardware.VisualTraits;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Maps OpenDB JSON records to the hardware domain model. This is the only class that knows OpenDB field
 * names. Values are validated against plausible ranges; problems are recorded as {@link app.platform.hardware.DataIssue}s
 * and the offending value is discarded rather than trusted.
 */
public final class OpenDbRecordMapper {

    public static final String SOURCE = "buildcores-opendb";

    /** Bump when mapping or validation rules change: the same upstream commit is then re-ingested. */
    public static final String VERSION = "opendb-mapper-v5";

    /** OpenDB category directory → domain category. */
    public static final Map<String, ComponentCategory> DIRECTORIES = Map.of(
            "CPU", ComponentCategory.CPU,
            "GPU", ComponentCategory.GPU,
            "Motherboard", ComponentCategory.MOTHERBOARD,
            "RAM", ComponentCategory.MEMORY,
            "Storage", ComponentCategory.STORAGE,
            "PSU", ComponentCategory.POWER_SUPPLY,
            "PCCase", ComponentCategory.CASE,
            "CPUCooler", ComponentCategory.CPU_COOLER);

    private static final Pattern KNOWN_SOCKET = Pattern.compile(
            "^(LGA (775|\\d{4}(-\\d)?( Narrow)?)|AM[1-5]\\+?|FM[12]\\+?|sTRX?[45]|TR4|sWRX8|SP[3-6]|G34|C32)$");
    private static final Pattern DIGITS = Pattern.compile("^\\d{8,14}$");
    private static final Pattern WATTS = Pattern.compile("^\\d+\\s*W$");

    public record ProductIdentifier(String type, String value) {
    }

    public record MappedRecord(HardwareComponent component, List<ProductIdentifier> identifiers) {
    }

    /** Returns empty when the record lacks the minimum identity (id and name) to be usable at all. */
    public Optional<MappedRecord> map(ComponentCategory category, JsonNode node) {
        FieldReader reader = new FieldReader(node);
        String externalId = reader.text("opendb_id");
        String name = reader.text("metadata.name");
        if (externalId == null || name == null) {
            return Optional.empty();
        }
        HardwareComponent component = switch (category) {
            case CPU -> cpu(reader, info(reader, category, externalId, name));
            case GPU -> gpu(reader, info(reader, category, externalId, name));
            case MOTHERBOARD -> motherboard(reader, info(reader, category, externalId, name));
            case MEMORY -> memory(reader, info(reader, category, externalId, name));
            case STORAGE -> storage(reader, info(reader, category, externalId, name));
            case POWER_SUPPLY -> powerSupply(reader, info(reader, category, externalId, name));
            case CASE -> pcCase(reader, info(reader, category, externalId, name));
            case CPU_COOLER -> cooler(reader, info(reader, category, externalId, name));
        };
        return Optional.of(new MappedRecord(withQuality(component, reader), identifiers(node)));
    }

    /** Info without quality yet; quality is attached after all fields were read. */
    private static ComponentInfo info(FieldReader reader, ComponentCategory category, String externalId, String name) {
        SourceRef source = new SourceRef(SOURCE, externalId);
        Integer year = reader.integerInRange("metadata.releaseYear", 1990, 2030, false);
        return new ComponentInfo(source.internalId(), source, category, name,
                reader.text("metadata.manufacturer"), reader.text("metadata.series"), reader.text("metadata.variant"), year, null,
                visual(reader, category));
    }

    /** Appearance data for 3D models. Never critical: missing values only make the model use typical proportions. */
    private static VisualTraits visual(FieldReader r, ComponentCategory category) {
        List<String> colors = new ArrayList<>(r.texts("color"));
        String lighting = r.texts("lighting").stream().findFirst().orElse(null);
        return switch (category) {
            case GPU -> {
                String cooling = r.text("cooling");
                yield new VisualTraits(colors, lighting, cooling, gpuFans(cooling, r.integer("fan_quantity")), r.integerInRange("fan_size", 40, 140, false),
                        null, null, null, null, null, null);
            }
            case CPU_COOLER -> new VisualTraits(colors, lighting, null, r.integerInRange("fan_quantity", 0, 6, false),
                    r.integerInRange("fan_size", 40, 140, false), null, null, null, null, null, null);
            case MEMORY -> new VisualTraits(colors, lighting, null, null, null, r.bool("heat_spreader"), r.bool("rgb"),
                    r.decimalInRange("height", 25, 70, false), null, null, null);
            case CASE -> new VisualTraits(colors, lighting, null, null, null, null, null,
                    r.decimalInRange("dimensions_mm.height", 150, 800, false), r.decimalInRange("dimensions_mm.width", 90, 400, false),
                    r.decimalInRange("dimensions_mm.depth", 150, 800, false), r.bool("power_supply_shroud"));
            default -> new VisualTraits(colors, lighting, null, null, null, null, null, null, null, null, null);
        };
    }

    private static final Pattern FANS = Pattern.compile("(\\d)\\s*fan", Pattern.CASE_INSENSITIVE);

    /** "2 Fans" → 2. Blower, passive and liquid designs have no count (null). */
    private static Integer gpuFans(String cooling, Integer declared) {
        if (cooling != null) {
            var matcher = FANS.matcher(cooling);
            if (matcher.find()) {
                return Integer.valueOf(matcher.group(1));
            }
            return null;
        }
        return declared;
    }

    private static Cpu cpu(FieldReader r, ComponentInfo info) {
        String socket = socket(r, "socket", true);
        String graphicsModel = r.text("specifications.integratedGraphics.model");
        Boolean integratedGraphics = r.node("specifications.integratedGraphics") == null ? null
                : graphicsModel != null && !graphicsModel.equalsIgnoreCase("None");
        return new Cpu(info, socket,
                r.text("microarchitecture"),
                r.integerInRange("cores.total", 1, 256, true),
                r.integerInRange("cores.performance", 0, 256, false),
                r.integerInRange("cores.efficiency", 0, 256, false),
                r.integerInRange("cores.threads", 1, 512, true),
                r.decimalInRange("clocks.performance.base", 0.3, 7.0, false),
                r.decimalInRange("clocks.performance.boost", 0.5, 7.0, true),
                positive(r.decimalInRange("clocks.efficiency.boost", 0.0, 7.0, false)),
                r.decimalInRange("cache.l3", 0.0, 1152.0, false),
                r.integerInRange("specifications.tdp", 5, 500, true),
                r.integerInRange("specifications.ppt", 5, 600, false),
                r.critical("specifications.integratedGraphics", integratedGraphics),
                integratedGraphics == Boolean.TRUE ? graphicsModel : null,
                r.critical("specifications.memory.types", r.texts("specifications.memory.types")),
                r.integerInRange("specifications.memory.maxSupport", 1, 8192, false),
                r.critical("specifications.includesCooler", r.bool("specifications.includesCooler")));
    }

    private static Gpu gpu(FieldReader r, ComponentInfo info) {
        Hardware.PcieLink link = Hardware.parsePcieInterface(r.text("interface"));
        Gpu.PowerConnectors connectors = null;
        if (r.node("power_connectors") != null) {
            connectors = new Gpu.PowerConnectors(
                    orZero(r.integer("power_connectors.pcie_6_pin")),
                    orZero(r.integer("power_connectors.pcie_8_pin")),
                    orZero(r.integer("power_connectors.pcie_12VHPWR")) + orZero(r.integer("power_connectors.pcie_12V_2x6")));
        }
        return new Gpu(info,
                r.text("chipset_manufacturer"),
                r.critical("chipset", r.text("chipset")),
                r.integerInRange("memory", 1, 128, true),
                r.text("memory_type"),
                r.integerInRange("core_count", 16, 50000, true),
                r.integerInRange("core_boost_clock", 200, 4000, true),
                r.integerInRange("tdp", 10, 700, true),
                r.integerInRange("length", 100, 450, true),
                r.decimalInRange("total_slot_width", 1.0, 5.0, false),
                link == null ? null : link.generation(),
                link == null ? null : link.lanes(),
                r.critical("power_connectors", connectors));
    }

    private static Motherboard motherboard(FieldReader r, ComponentInfo info) {
        List<Motherboard.M2Slot> m2Slots = null;
        JsonNode m2 = r.node("m2_slots");
        if (m2 != null && m2.isArray()) {
            m2Slots = new ArrayList<>();
            for (JsonNode slot : m2) {
                String size = slot.path("size").isString() ? slot.path("size").stringValue() : "";
                Set<String> sizes = Arrays.stream(size.split("[/,]")).map(String::trim).filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                m2Slots.add(new Motherboard.M2Slot(sizes,
                        slot.path("key").isString() ? slot.path("key").stringValue() : null,
                        slot.path("interface").isString() ? slot.path("interface").stringValue() : null));
            }
        }
        List<Motherboard.PcieSlot> pcieSlots = null;
        JsonNode pcie = r.node("pcie_slots");
        if (pcie != null && pcie.isArray()) {
            pcieSlots = new ArrayList<>();
            for (JsonNode slot : pcie) {
                if (slot.path("lanes").isNumber()) {
                    pcieSlots.add(new Motherboard.PcieSlot(
                            slot.path("gen").isString() ? slot.path("gen").stringValue() : null,
                            slot.path("quantity").isNumber() ? slot.path("quantity").intValue() : 1,
                            slot.path("lanes").intValue()));
                }
            }
        }
        // An empty list in the source usually means "not filled in", not "none": treat it as unknown.
        if (m2Slots != null && m2Slots.isEmpty()) {
            m2Slots = null;
        }
        if (pcieSlots != null && pcieSlots.isEmpty()) {
            pcieSlots = null;
        }
        Integer sata6 = r.integer("storage_devices.sata_6_gb_s");
        Integer sata3 = r.integer("storage_devices.sata_3_gb_s");
        Integer sata = sata6 == null && sata3 == null ? null : orZero(sata6) + orZero(sata3);
        if (sata != null && sata == 0) {
            // Desktop boards essentially always have SATA ports; a zero here is a data-entry gap, not a fact.
            r.unrecognized("storage_devices", "0 portas SATA é implausível para uma placa-mãe de desktop; tratado como desconhecido", false);
            sata = null;
        }
        return new Motherboard(info,
                socket(r, "socket", true),
                r.critical("chipset", r.text("chipset")),
                r.critical("form_factor", r.text("form_factor")),
                r.critical("memory.ram_type", r.text("memory.ram_type")),
                r.integerInRange("memory.slots", 1, 16, true),
                r.integerInRange("memory.max", 1, 8192, true),
                r.critical("m2_slots", m2Slots),
                sata,
                r.critical("pcie_slots", pcieSlots),
                r.text("wireless_networking"));
    }

    private static Memory memory(FieldReader r, ComponentInfo info) {
        String ecc = r.text("ecc");
        String registered = r.text("registered");
        return new Memory(info,
                r.critical("ram_type", r.text("ram_type")),
                r.critical("form_factor", r.text("form_factor")),
                r.integerInRange("speed", 100, 12000, true),
                r.integerInRange("cas_latency", 1, 80, false),
                r.integerInRange("modules.quantity", 1, 16, true),
                r.integerInRange("modules.capacity_gb", 1, 512, false),
                r.integerInRange("capacity", 1, 2048, true),
                ecc == null ? null : !ecc.toLowerCase().startsWith("non"),
                registered == null ? null : registered.toLowerCase().startsWith("registered"));
    }

    private static Storage storage(FieldReader r, ComponentInfo info) {
        return new Storage(info,
                r.critical("storage_type", r.text("storage_type")),
                r.integerInRange("capacity", 1, 100000, true),
                r.critical("form_factor", r.text("form_factor")),
                r.critical("interface", r.text("interface")),
                r.bool("nvme"));
    }

    private static PowerSupply powerSupply(FieldReader r, ComponentInfo info) {
        return new PowerSupply(info,
                r.integerInRange("wattage", 100, 3000, true),
                r.critical("form_factor", r.text("form_factor")),
                r.critical("efficiency_rating", r.text("efficiency_rating")),
                r.text("modular"),
                r.integerInRange("length", 80, 300, false),
                r.integerInRange("connectors.pcie_6_plus_2_pin", 0, 16, true),
                r.integerInRange("connectors.pcie_12vhpwr", 0, 8, false),
                r.integerInRange("connectors.eps_8_pin", 0, 8, false));
    }

    private static PcCase pcCase(FieldReader r, ComponentInfo info) {
        Boolean includesPsu = r.bool("power_supply_included");
        if (includesPsu == null) {
            String psu = r.text("power_supply");
            if (psu != null) {
                includesPsu = WATTS.matcher(psu).matches();
            }
        }
        return new PcCase(info,
                r.critical("form_factor", r.text("form_factor")),
                r.critical("supported_motherboard_form_factors", r.texts("supported_motherboard_form_factors")),
                r.integerInRange("max_video_card_length", 100, 600, true),
                r.integerInRange("max_cpu_cooler_height", 20, 250, true),
                r.integerInRange("max_psu_length", 80, 400, false),
                r.texts("supported_power_supply_form_factors"),
                includesPsu,
                r.integerInRange("expansion_slots", 0, 20, false),
                r.decimalInRange("volume", 1.0, 200.0, false),
                r.bool("has_transparent_side_panel"));
    }

    private static CpuCooler cooler(FieldReader r, ComponentInfo info) {
        Set<String> sockets = new LinkedHashSet<>();
        for (String value : r.texts("cpu_sockets")) {
            String normalized = Hardware.normalizeSocket(value);
            if (KNOWN_SOCKET.matcher(normalized).matches()) {
                sockets.add(normalized);
            } else {
                r.unrecognized("cpu_sockets", "Encaixe desconhecido descartado: \"" + value + "\"", false);
            }
        }
        Boolean water = r.critical("water_cooled", r.bool("water_cooled"));
        return new CpuCooler(info,
                r.critical("cpu_sockets", sockets),
                water,
                r.integerInRange("height", 20, 200, Boolean.FALSE.equals(water)),
                r.integerInRange("radiator_size", 80, 480, Boolean.TRUE.equals(water)),
                r.bool("fanless"),
                r.integerInRange("fan_quantity", 0, 6, false));
    }

    private static String socket(FieldReader r, String path, boolean critical) {
        String raw = r.text(path);
        if (raw == null) {
            return critical ? r.critical(path, null) : null;
        }
        String normalized = Hardware.normalizeSocket(raw);
        if (!normalized.equals(raw)) {
            r.normalized(path, "\"" + raw + "\" tratado como \"" + normalized + "\"");
        }
        return critical ? r.critical(path, normalized) : normalized;
    }

    private static List<ProductIdentifier> identifiers(JsonNode node) {
        List<ProductIdentifier> result = new ArrayList<>();
        JsonNode list = node.path("identifiers").path("identifiers");
        if (!list.isArray()) {
            return result;
        }
        for (JsonNode item : list) {
            String type = item.path("type").isString() ? item.path("type").stringValue().toLowerCase() : "";
            String value = item.path("value").isString() ? item.path("value").stringValue().trim() : "";
            boolean valid = switch (type) {
                case "ean", "upc", "gtin" -> DIGITS.matcher(value).matches();
                // Some MPNs in the source are several part numbers concatenated; those are not usable for matching.
                case "mpn" -> !value.isEmpty() && value.length() <= 40;
                default -> false;
            };
            if (valid) {
                result.add(new ProductIdentifier(type, value));
            }
        }
        return result;
    }

    private static HardwareComponent withQuality(HardwareComponent component, FieldReader reader) {
        ComponentInfo base = component.info();
        ComponentInfo info = new ComponentInfo(base.id(), base.source(), base.category(), base.name(), base.manufacturer(),
                base.series(), base.variant(), base.releaseYear(), new DataQuality(reader.qualityScore(), reader.issues()), base.visual());
        return switch (component) {
            case Cpu c -> new Cpu(info, c.socket(), c.microarchitecture(), c.cores(), c.performanceCores(), c.efficiencyCores(),
                    c.threads(), c.baseClockGhz(), c.boostClockGhz(), c.efficiencyBoostClockGhz(), c.l3CacheMb(), c.tdpWatts(),
                    c.maxPowerWatts(), c.integratedGraphics(), c.integratedGraphicsModel(), c.memoryTypes(), c.maxMemoryGb(), c.includesCooler());
            case Gpu g -> new Gpu(info, g.chipsetManufacturer(), g.chipset(), g.vramGb(), g.memoryType(), g.coreCount(),
                    g.boostClockMhz(), g.tdpWatts(), g.lengthMm(), g.slotWidth(), g.pcieGeneration(), g.pcieLanes(), g.powerConnectors());
            case Motherboard m -> new Motherboard(info, m.socket(), m.chipset(), m.formFactor(), m.ramType(), m.memorySlots(),
                    m.maxMemoryGb(), m.m2Slots(), m.sataPorts(), m.pcieSlots(), m.wireless());
            case Memory m -> new Memory(info, m.ramType(), m.formFactor(), m.speedMts(), m.casLatency(), m.modules(),
                    m.moduleCapacityGb(), m.totalCapacityGb(), m.ecc(), m.registered());
            case Storage s -> new Storage(info, s.storageType(), s.capacityGb(), s.formFactor(), s.interfaceName(), s.nvme());
            case PowerSupply p -> new PowerSupply(info, p.wattage(), p.formFactor(), p.efficiencyRating(), p.modular(), p.lengthMm(),
                    p.pcieEightPinConnectors(), p.highPower16PinConnectors(), p.cpuEightPinConnectors());
            case PcCase c -> new PcCase(info, c.formFactor(), c.supportedMotherboardFormFactors(), c.maxGpuLengthMm(),
                    c.maxCoolerHeightMm(), c.maxPsuLengthMm(), c.supportedPsuFormFactors(), c.includesPowerSupply(),
                    c.expansionSlots(), c.volumeLiters(), c.transparentSidePanel());
            case CpuCooler c -> new CpuCooler(info, c.sockets(), c.waterCooled(), c.heightMm(), c.radiatorSizeMm(), c.fanless(), c.fanCount());
        };
    }

    private static Double positive(Double value) {
        return value == null || value <= 0 ? null : value;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
