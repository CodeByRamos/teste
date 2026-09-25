package app.platform.compatibility;

import app.platform.hardware.ComponentCategory;

import java.util.ArrayList;
import java.util.List;

import static app.platform.hardware.ComponentCategory.CPU;
import static app.platform.hardware.ComponentCategory.CPU_COOLER;
import static app.platform.hardware.ComponentCategory.GPU;

/** Parts a desktop PC cannot run without, including video output and CPU cooling. */
final class EssentialPartsRule implements CompatibilityRule {

    private static final List<ComponentCategory> REQUIRED = List.of(
            ComponentCategory.CPU, ComponentCategory.MOTHERBOARD, ComponentCategory.MEMORY,
            ComponentCategory.STORAGE, ComponentCategory.POWER_SUPPLY, ComponentCategory.CASE);

    @Override
    public List<CompatibilityFinding> evaluate(BuildParts parts) {
        List<CompatibilityFinding> findings = new ArrayList<>();

        List<String> missing = REQUIRED.stream()
                .filter(category -> !parts.has(category))
                .map(category -> category.label().toLowerCase())
                .toList();
        if (!missing.isEmpty()) {
            findings.add(CompatibilityFinding.warning("essential.missing", REQUIRED,
                    "Ainda faltam peças",
                    "Para ter um PC completo ainda falta escolher: " + String.join(", ", missing) + ".",
                    null));
        }

        if (parts.cpu() != null && parts.gpu() == null) {
            Boolean integrated = parts.cpu().integratedGraphics();
            if (Boolean.FALSE.equals(integrated)) {
                findings.add(CompatibilityFinding.incompatible("essential.video", List.of(CPU, GPU),
                        "Sem saída de vídeo",
                        "O processador não tem vídeo integrado e não há placa de vídeo, então o computador não mostraria imagem no monitor.",
                        "Vídeo integrado: não"));
            } else if (integrated == null) {
                findings.add(CompatibilityFinding.unverifiable("essential.video", List.of(CPU, GPU),
                        "Saída de vídeo não confirmada",
                        "Não conseguimos confirmar se este processador tem vídeo integrado. Sem placa de vídeo, o PC pode ficar sem imagem."));
            } else {
                findings.add(CompatibilityFinding.ok("essential.video", List.of(CPU, GPU),
                        "Imagem pelo vídeo integrado",
                        "O processador tem vídeo integrado, então o monitor pode ser ligado direto na placa-mãe.",
                        parts.cpu().integratedGraphicsModel()));
            }
        }

        if (parts.cpu() != null && parts.cooler() == null) {
            Boolean bundled = parts.cpu().includesCooler();
            if (Boolean.TRUE.equals(bundled)) {
                findings.add(CompatibilityFinding.ok("essential.cooling", List.of(CPU, CPU_COOLER),
                        "Cooler incluso no processador",
                        "Este processador já vem com um cooler na caixa, suficiente para o uso normal.",
                        null));
            } else if (Boolean.FALSE.equals(bundled)) {
                findings.add(CompatibilityFinding.incompatible("essential.cooling", List.of(CPU, CPU_COOLER),
                        "Falta um cooler",
                        "Este processador é vendido sem cooler, e sem um ele superaquece e desliga.",
                        "Cooler incluso: não"));
            } else {
                findings.add(CompatibilityFinding.unverifiable("essential.cooling", List.of(CPU, CPU_COOLER),
                        "Cooler não confirmado",
                        "Não sabemos se este processador vem com cooler na caixa. Confirme antes de comprar ou inclua um cooler."));
            }
        }
        return findings;
    }
}
