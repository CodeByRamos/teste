package app.platform.visualization;

import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.Gpu;
import app.platform.hardware.HardwareComponent;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.Storage;
import app.platform.hardware.VisualTraits;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps a component to the parametric 3D model family that represents it: component → family → variant →
 * dimensions → visual parameters. One base model covers every component of its family; nothing here is
 * per-SKU. Classification rules match the coverage analysis in docs/3d/MODEL_LIBRARY.md.
 */
public final class ModelResolver {

    public static final String VERSION = "models-v1";

    /** Every model id this resolver can return; kept in sync with the generated catalog by a test. */
    public static final Set<String> MODEL_IDS = Set.of(
            "gpu-single-fan-v1", "gpu-dual-fan-v1", "gpu-triple-fan-v1",
            "mb-atx-v1", "mb-matx-v1", "mb-itx-v1", "mb-eatx-v1",
            "case-atx-mid-tower-v1", "case-atx-full-tower-v1", "case-matx-tower-v1", "case-itx-tower-v1",
            "cooler-single-tower-v1", "cooler-dual-tower-v1", "cooler-low-profile-v1",
            "aio-radiator-120-v1", "aio-radiator-240-v1", "aio-radiator-280-v1", "aio-radiator-360-v1", "aio-pump-v1",
            "ram-standard-v1", "ram-heatsink-v1", "ram-rgb-v1",
            "psu-atx-v1", "psu-sfx-v1", "psu-sfx-l-v1",
            "ssd-m2-v1", "drive-25-v1", "hdd-35-v1");

    private static final Pattern DUAL_TOWER_NAME = Pattern.compile(
            "dual|twin|NH-D1[45]|Phantom Spirit|Peerless Assassin|FUMA|Dark Rock Pro|AK620|Frost Commander", Pattern.CASE_INSENSITIVE);
    private static final Pattern M2_LENGTH = Pattern.compile("M\\.2-22(\\d{2,3})");

    private ModelResolver() {
    }

    /** Models that draw this component (an AIO is a radiator plus a pump); empty when none applies. */
    public static List<ModelSpec> resolve(HardwareComponent component) {
        VisualTraits visual = component.info().visual();
        return switch (component) {
            case Gpu gpu -> List.of(gpu(gpu, visual));
            case Motherboard board -> List.of(board(board, visual));
            case PcCase pcCase -> List.of(pcCase(pcCase, visual));
            case CpuCooler cooler -> cooler(cooler, visual);
            case Memory memory -> memory(memory, visual);
            case PowerSupply psu -> List.of(psu(psu, visual));
            case Storage storage -> storage(storage);
            // The processor is drawn as part of the motherboard (it sits under the cooler).
            case Cpu cpu -> List.of();
        };
    }

    private static ModelSpec gpu(Gpu gpu, VisualTraits visual) {
        List<String> assumptions = new ArrayList<>();
        Integer fans = visual.fanCount();
        String cooling = visual.cooling() == null ? "" : visual.cooling().toLowerCase();
        if (fans == null) {
            fans = cooling.contains("blower") || cooling.contains("passive") ? 1 : 2;
            assumptions.add(cooling.isEmpty()
                    ? "O tipo de refrigeração não está informado: desenhamos com duas ventoinhas."
                    : "Ainda não temos um modelo próprio para placas \"" + visual.cooling() + "\": usamos o mais parecido.");
        }
        int fanCount = Math.min(3, Math.max(1, fans));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fanCount", fanCount);
        boolean measured = gpu.lengthMm() != null;
        if (gpu.lengthMm() != null) {
            params.put("lengthMm", gpu.lengthMm());
            params.put("backplate", gpu.lengthMm() >= 220);
            // Card height is not in the data; longer cards tend to be taller.
            params.put("heightMm", gpu.lengthMm() < 200 ? 112 : gpu.lengthMm() < 280 ? 120 : 132);
            assumptions.add("A altura da placa não está nos dados: usamos um valor típico para o comprimento dela.");
        } else {
            assumptions.add("O comprimento da placa não está nos dados: usamos um tamanho típico.");
        }
        if (gpu.slotWidth() != null && gpu.slotWidth() >= 1) {
            params.put("slots", Math.round(gpu.slotWidth() * 2) / 2.0);
        } else {
            measured = false;
            assumptions.add("A espessura (número de slots) não está nos dados: usamos um valor típico.");
        }
        if (gpu.powerConnectors() != null) {
            params.put("eightPinConnectors", gpu.powerConnectors().eightPin() + gpu.powerConnectors().sixPin());
            params.put("highPowerConnector", gpu.powerConnectors().highPower16Pin() > 0);
        }
        String modelId = switch (fanCount) {
            case 1 -> "gpu-single-fan-v1";
            case 3 -> "gpu-triple-fan-v1";
            default -> "gpu-dual-fan-v1";
        };
        return spec(modelId, "gpu", params, visual, visual.hasLighting(), measured, assumptions);
    }

    private static ModelSpec board(Motherboard board, VisualTraits visual) {
        List<String> assumptions = new ArrayList<>();
        String form = board.formFactor() == null ? "" : board.formFactor();
        String variant;
        if (form.equals("ATX")) {
            variant = "atx";
        } else if (form.equals("Micro ATX")) {
            variant = "matx";
        } else if (form.contains("ITX") || form.contains("DTX")) {
            variant = "itx";
        } else if (form.contains("EATX") || form.contains("SSI") || form.contains("XL") || form.contains("HPTX")) {
            variant = "eatx";
        } else {
            variant = "atx";
            assumptions.add("Formato da placa-mãe não reconhecido: desenhamos como ATX.");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("form", variant);
        if (board.memorySlots() != null) params.put("ramSlots", Math.min(4, board.memorySlots()));
        if (board.m2Slots() != null) params.put("m2Slots", Math.min(3, board.driveM2Slots().size()));
        if (board.pcieSlots() != null) {
            int x16 = board.pcieSlots().stream().filter(slot -> slot.lanes() >= 16).mapToInt(Motherboard.PcieSlot::quantity).sum();
            params.put("x16Slots", Math.max(1, x16));
        }
        return spec("mb-" + variant + "-v1", "motherboard", params, visual, visual.hasLighting(), board.formFactor() != null, assumptions);
    }

    private static ModelSpec pcCase(PcCase pcCase, VisualTraits visual) {
        List<String> assumptions = new ArrayList<>();
        String form = pcCase.formFactor() == null ? "" : pcCase.formFactor();
        String variant;
        String layoutForm;
        if (form.contains("Full Tower")) {
            variant = "atx-full-tower";
            layoutForm = "atx-full";
        } else if (form.startsWith("Micro ATX") || form.equals("ATX Mini Tower")) {
            variant = "matx-tower";
            layoutForm = "matx";
        } else if (form.startsWith("Mini ITX")) {
            variant = "itx-tower";
            layoutForm = "itx";
        } else {
            variant = "atx-mid-tower";
            layoutForm = "atx-mid";
            if (!form.contains("Mid Tower")) {
                assumptions.add("Ainda não temos um modelo para gabinetes \"" + form + "\": desenhamos como mid tower.");
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("form", layoutForm);
        boolean measured = visual.widthMm() != null && visual.heightMm() != null && visual.depthMm() != null;
        if (measured) {
            params.put("widthMm", visual.widthMm());
            params.put("heightMm", visual.heightMm());
            params.put("depthMm", visual.depthMm());
        } else {
            assumptions.add("As medidas externas do gabinete não estão nos dados: usamos as medidas típicas de um " + form.toLowerCase() + ".");
        }
        if (visual.psuShroud() != null) params.put("psuShroud", visual.psuShroud());
        if (pcCase.transparentSidePanel() != null) params.put("glassSide", pcCase.transparentSidePanel());
        if (pcCase.expansionSlots() != null && pcCase.expansionSlots() > 0) params.put("expansionSlots", pcCase.expansionSlots());
        return spec("case-" + variant + "-v1", "case", params, visual, visual.hasLighting(), measured, assumptions);
    }

    private static List<ModelSpec> cooler(CpuCooler cooler, VisualTraits visual) {
        List<String> assumptions = new ArrayList<>();
        if (Boolean.TRUE.equals(cooler.waterCooled())) {
            Integer size = cooler.radiatorSizeMm();
            String label;
            int fans;
            int fanSize;
            if (size == null) {
                label = "240";
                fans = 2;
                fanSize = 120;
                assumptions.add("O tamanho do radiador não está nos dados: desenhamos um de 240 mm.");
            } else if (size <= 140) {
                label = "120";
                fans = 1;
                fanSize = 120;
            } else if (size == 280) {
                label = "280";
                fans = 2;
                fanSize = 140;
            } else if (size >= 360) {
                label = "360";
                fans = 3;
                fanSize = 120;
                if (size > 360) assumptions.add("Radiador de " + size + " mm desenhado com o modelo de 360 mm.");
            } else {
                label = "240";
                fans = 2;
                fanSize = 120;
            }
            Map<String, Object> radiator = new LinkedHashMap<>();
            radiator.put("fanCount", fans);
            radiator.put("fanSizeMm", fanSize);
            return List.of(
                    spec("aio-radiator-" + label + "-v1", "aio-radiator", radiator, visual, visual.hasLighting(), size != null, assumptions),
                    spec("aio-pump-v1", "aio-pump", Map.of(), visual, visual.hasLighting(), true, List.of()));
        }
        Map<String, Object> params = new LinkedHashMap<>();
        String style;
        Integer height = cooler.heightMm();
        if (height == null) {
            style = "single-tower";
            assumptions.add("A altura do cooler não está nos dados: desenhamos uma torre de tamanho típico.");
        } else if (height < 80) {
            style = "low-profile";
        } else {
            boolean twoFans = visual.fanCount() != null && visual.fanCount() >= 2;
            style = DUAL_TOWER_NAME.matcher(cooler.name()).find() || twoFans && height >= 150 ? "dual-tower" : "single-tower";
        }
        params.put("style", style);
        if (height != null) params.put("heightMm", height);
        if (visual.fanSizeMm() != null) params.put("fanSizeMm", visual.fanSizeMm());
        if (visual.fanCount() != null && visual.fanCount() > 0) params.put("fanCount", visual.fanCount());
        return List.of(spec("cooler-" + style + "-v1", "air-cooler", params, visual, visual.hasLighting(), height != null, assumptions));
    }

    private static List<ModelSpec> memory(Memory memory, VisualTraits visual) {
        if (Boolean.TRUE.equals(memory.isLaptopModule())) {
            return List.of();
        }
        List<String> assumptions = new ArrayList<>();
        boolean rgb = Boolean.TRUE.equals(visual.rgb());
        boolean spreader = rgb || Boolean.TRUE.equals(visual.heatSpreader());
        String variant = rgb ? "rgb" : spreader ? "heatsink" : "standard";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("heatSpreader", spreader);
        params.put("rgb", rgb);
        if (visual.heightMm() != null) {
            params.put("heightMm", visual.heightMm());
        } else if (spreader) {
            assumptions.add("A altura do pente não está nos dados: usamos a altura típica de memórias com dissipador.");
        }
        if (memory.modules() != null) params.put("modules", memory.modules());
        return List.of(spec("ram-" + variant + "-v1", "ram", params, visual, rgb, visual.heightMm() != null || !spreader, assumptions));
    }

    private static ModelSpec psu(PowerSupply psu, VisualTraits visual) {
        List<String> assumptions = new ArrayList<>();
        String form = psu.formFactor() == null ? "" : psu.formFactor();
        String variant = switch (form) {
            case "SFX" -> "sfx";
            case "SFX-L" -> "sfx-l";
            default -> "atx";
        };
        if (!form.equals("ATX") && variant.equals("atx")) {
            assumptions.add("Ainda não temos um modelo para fontes \"" + form + "\": desenhamos como ATX.");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("form", variant);
        if (psu.lengthMm() != null) params.put("lengthMm", psu.lengthMm());
        else assumptions.add("O comprimento da fonte não está nos dados: usamos o tamanho padrão.");
        if (psu.modular() != null) params.put("modular", !psu.modular().equals("Non-Modular"));
        return spec("psu-" + variant + "-v1", "psu", params, visual, visual.hasLighting(), psu.lengthMm() != null, assumptions);
    }

    private static List<ModelSpec> storage(Storage storage) {
        String form = storage.formFactor() == null ? "" : storage.formFactor();
        if (form.startsWith("M.2")) {
            Map<String, Object> params = new LinkedHashMap<>();
            Matcher length = M2_LENGTH.matcher(form);
            params.put("lengthMm", length.find() ? Integer.parseInt(length.group(1)) : 80);
            return List.of(spec("ssd-m2-v1", "m2", params, VisualTraits.NONE, false, true, List.of()));
        }
        if (form.equals("2.5\"")) {
            return List.of(spec("drive-25-v1", "drive-25", Map.of(), VisualTraits.NONE, false, true, List.of()));
        }
        if (form.equals("3.5\"")) {
            return List.of(spec("hdd-35-v1", "drive-35", Map.of(), VisualTraits.NONE, false, true, List.of()));
        }
        return List.of();
    }

    private static ModelSpec spec(String modelId, String family, Map<String, Object> params, VisualTraits visual, boolean rgb,
                                  boolean measured, List<String> assumptions) {
        return new ModelSpec(modelId, family, params, visual.primaryColor(), rgb, measured ? "measured" : "estimated", assumptions);
    }
}
