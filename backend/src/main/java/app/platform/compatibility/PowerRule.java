package app.platform.compatibility;

import app.platform.hardware.Gpu;
import app.platform.hardware.PowerSupply;

import java.util.ArrayList;
import java.util.List;

import static app.platform.hardware.ComponentCategory.CPU;
import static app.platform.hardware.ComponentCategory.GPU;
import static app.platform.hardware.ComponentCategory.POWER_SUPPLY;

final class PowerRule implements CompatibilityRule {

    @Override
    public List<CompatibilityFinding> evaluate(BuildParts parts) {
        PowerSupply psu = parts.powerSupply();
        if (psu == null) {
            return List.of();
        }
        List<CompatibilityFinding> findings = new ArrayList<>();
        PowerEstimate power = PowerEstimator.estimate(parts);

        if (psu.wattage() == null) {
            findings.add(CompatibilityFinding.unverifiable("power.capacity", List.of(POWER_SUPPLY),
                    "Potência da fonte não confirmada",
                    "Não conseguimos confirmar a potência desta fonte."));
        } else if (parts.cpu() != null || parts.gpu() != null) {
            String detail = "Consumo estimado: " + power.estimatedLoadWatts() + " W · Recomendado: "
                    + power.recommendedPsuWatts() + " W · Fonte: " + psu.wattage() + " W";
            if (psu.wattage() < power.estimatedLoadWatts()) {
                findings.add(CompatibilityFinding.incompatible("power.capacity", List.of(POWER_SUPPLY, CPU, GPU),
                        "Fonte fraca demais",
                        "A fonte de " + psu.wattage() + " W não aguenta o consumo estimado de " + power.estimatedLoadWatts()
                                + " W. O computador poderia desligar sozinho durante jogos ou tarefas pesadas.",
                        detail));
            } else if (psu.wattage() < power.recommendedPsuWatts()) {
                findings.add(CompatibilityFinding.warning("power.capacity", List.of(POWER_SUPPLY, CPU, GPU),
                        "Fonte com pouca folga",
                        "A fonte dá conta do consumo estimado, mas com pouca margem para picos de energia e upgrades futuros. "
                                + "Recomendamos pelo menos " + power.recommendedPsuWatts() + " W.",
                        detail));
            } else {
                findings.add(CompatibilityFinding.ok("power.capacity", List.of(POWER_SUPPLY, CPU, GPU),
                        "A fonte tem potência suficiente",
                        "Aguenta o consumo estimado com folga para picos de energia.",
                        detail));
            }
            if (!power.complete()) {
                findings.add(CompatibilityFinding.unverifiable("power.estimate", List.of(POWER_SUPPLY, CPU, GPU),
                        "Estimativa de consumo incompleta",
                        "Falta o consumo de energia do processador ou da placa de vídeo, então a estimativa pode estar abaixo do real."));
            }
        }

        Gpu gpu = parts.gpu();
        if (gpu != null) {
            Gpu.PowerConnectors needs = gpu.powerConnectors();
            switch (Fit.psuConnectorsForGpu(psu, gpu)) {
                case NO -> {
                    if (needs.highPower16Pin() > 0) {
                        findings.add(CompatibilityFinding.warning("power.connectors", List.of(POWER_SUPPLY, GPU),
                                "A fonte não tem o conector novo da placa de vídeo",
                                "A placa de vídeo usa o conector de 16 pinos (12VHPWR / 12V-2x6). Ela costuma vir com um adaptador, "
                                        + "mas uma fonte com esse conector nativo é mais segura e organizada.",
                                null));
                    } else {
                        findings.add(CompatibilityFinding.incompatible("power.connectors", List.of(POWER_SUPPLY, GPU),
                                "Faltam cabos de energia para a placa de vídeo",
                                "A placa de vídeo precisa de " + needs.totalPcieCables() + " cabos de energia PCIe, e a fonte só tem "
                                        + psu.pcieEightPinConnectors() + ".",
                                null));
                    }
                }
                case UNKNOWN -> findings.add(CompatibilityFinding.unverifiable("power.connectors", List.of(POWER_SUPPLY, GPU),
                        "Cabos de energia não confirmados",
                        "Não conseguimos confirmar se a fonte tem os cabos que a placa de vídeo precisa."));
                case YES -> findings.add(CompatibilityFinding.ok("power.connectors", List.of(POWER_SUPPLY, GPU),
                        "A fonte tem os cabos da placa de vídeo",
                        "A fonte tem os conectores de energia que a placa de vídeo usa.",
                        null));
            }
        }
        return findings;
    }
}
