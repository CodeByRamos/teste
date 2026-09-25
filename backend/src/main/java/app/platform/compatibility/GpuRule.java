package app.platform.compatibility;

import app.platform.hardware.Gpu;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;

import java.util.ArrayList;
import java.util.List;

import static app.platform.hardware.ComponentCategory.CASE;
import static app.platform.hardware.ComponentCategory.GPU;
import static app.platform.hardware.ComponentCategory.MOTHERBOARD;

final class GpuRule implements CompatibilityRule {

    @Override
    public List<CompatibilityFinding> evaluate(BuildParts parts) {
        Gpu gpu = parts.gpu();
        if (gpu == null) {
            return List.of();
        }
        List<CompatibilityFinding> findings = new ArrayList<>();

        Motherboard board = parts.motherboard();
        if (board != null) {
            switch (Fit.gpuSlot(board)) {
                case NO -> findings.add(CompatibilityFinding.incompatible("gpu.slot", List.of(GPU, MOTHERBOARD),
                        "Sem encaixe para a placa de vídeo",
                        "A placa-mãe não tem o encaixe longo (PCIe x16) usado por placas de vídeo.",
                        null));
                case UNKNOWN -> findings.add(CompatibilityFinding.unverifiable("gpu.slot", List.of(GPU, MOTHERBOARD),
                        "Encaixe da placa de vídeo não confirmado",
                        "Não conseguimos confirmar os encaixes de expansão desta placa-mãe."));
                case YES -> {
                    Integer boardGen = board.bestFullLengthPcieGeneration();
                    boolean narrowLink = gpu.pcieLanes() != null && gpu.pcieLanes() <= 8;
                    if (narrowLink && boardGen != null && boardGen <= 3) {
                        findings.add(CompatibilityFinding.warning("gpu.slot", List.of(GPU, MOTHERBOARD),
                                "Pode perder um pouco de desempenho",
                                "Esta placa de vídeo usa uma conexão mais estreita e, numa placa-mãe PCIe " + boardGen
                                        + ".0, pode render um pouco menos em alguns jogos. Funciona normalmente.",
                                "Placa de vídeo: x" + gpu.pcieLanes() + " · Placa-mãe: PCIe " + boardGen + ".0"));
                    } else {
                        findings.add(CompatibilityFinding.ok("gpu.slot", List.of(GPU, MOTHERBOARD),
                                "A placa de vídeo encaixa na placa-mãe",
                                "A placa-mãe tem o encaixe PCIe x16 usado por placas de vídeo.",
                                null));
                    }
                }
            }
        }

        PcCase pcCase = parts.pcCase();
        if (pcCase != null) {
            switch (Fit.gpuLengthInCase(gpu, pcCase)) {
                case NO -> findings.add(CompatibilityFinding.incompatible("gpu.case", List.of(GPU, CASE),
                        "A placa de vídeo não cabe no gabinete",
                        "A placa de vídeo tem " + gpu.lengthMm() + " mm de comprimento, e o gabinete comporta até " + pcCase.maxGpuLengthMm() + " mm.",
                        null));
                case UNKNOWN -> findings.add(CompatibilityFinding.unverifiable("gpu.case", List.of(GPU, CASE),
                        "Espaço para a placa de vídeo não confirmado",
                        "Falta o comprimento da placa de vídeo ou o limite do gabinete, então não conseguimos confirmar que ela cabe."));
                case YES -> {
                    int clearance = pcCase.maxGpuLengthMm() - gpu.lengthMm();
                    String detail = "Placa de vídeo: " + gpu.lengthMm() + " mm · Limite do gabinete: " + pcCase.maxGpuLengthMm() + " mm";
                    findings.add(clearance < Fit.TIGHT_CLEARANCE_MM
                            ? CompatibilityFinding.warning("gpu.case", List.of(GPU, CASE),
                                    "A placa de vídeo cabe, mas bem justa",
                                    "Sobram só " + clearance + " mm. Cabos e ventoinhas na frente do gabinete podem atrapalhar.", detail)
                            : CompatibilityFinding.ok("gpu.case", List.of(GPU, CASE),
                                    "A placa de vídeo cabe no gabinete",
                                    "Sobram " + clearance + " mm de folga.", detail));
                }
            }
        }
        return findings;
    }
}
