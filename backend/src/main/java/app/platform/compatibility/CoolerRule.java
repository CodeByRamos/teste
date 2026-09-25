package app.platform.compatibility;

import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.PcCase;

import java.util.ArrayList;
import java.util.List;

import static app.platform.hardware.ComponentCategory.CASE;
import static app.platform.hardware.ComponentCategory.CPU;
import static app.platform.hardware.ComponentCategory.CPU_COOLER;

final class CoolerRule implements CompatibilityRule {

    /**
     * Heuristic: the data has no cooler thermal rating, so a compact air cooler (below this height)
     * paired with a high-power CPU is flagged for attention rather than rejected.
     */
    static final int HIGH_POWER_CPU_WATTS = 150;
    static final int COMPACT_COOLER_HEIGHT_MM = 130;

    @Override
    public List<CompatibilityFinding> evaluate(BuildParts parts) {
        CpuCooler cooler = parts.cooler();
        if (cooler == null) {
            return List.of();
        }
        List<CompatibilityFinding> findings = new ArrayList<>();
        Cpu cpu = parts.cpu();
        if (cpu != null) {
            switch (Fit.coolerSocket(cooler, cpu)) {
                case NO -> findings.add(CompatibilityFinding.incompatible("cooler.socket", List.of(CPU_COOLER, CPU),
                        "O cooler não prende neste processador",
                        "O cooler não tem suporte de fixação para o encaixe " + cpu.socket() + ".",
                        "Encaixes suportados pelo cooler: " + String.join(", ", cooler.sockets())));
                case UNKNOWN -> findings.add(CompatibilityFinding.unverifiable("cooler.socket", List.of(CPU_COOLER, CPU),
                        "Fixação do cooler não confirmada",
                        "Não conseguimos confirmar se o cooler tem suporte para o encaixe deste processador."));
                case YES -> findings.add(CompatibilityFinding.ok("cooler.socket", List.of(CPU_COOLER, CPU),
                        "O cooler prende no processador",
                        "O cooler tem suporte de fixação para o encaixe " + cpu.socket() + ".",
                        null));
            }
            Integer power = cpu.powerBudgetWatts();
            if (power != null && power >= HIGH_POWER_CPU_WATTS && cooler.isAirCooler()
                    && cooler.heightMm() != null && cooler.heightMm() < COMPACT_COOLER_HEIGHT_MM) {
                findings.add(CompatibilityFinding.warning("cooler.capacity", List.of(CPU_COOLER, CPU),
                        "Cooler pequeno para este processador",
                        "Este processador pode consumir bastante energia sob carga pesada, e um cooler compacto pode deixá-lo quente e barulhento. "
                                + "Um cooler maior é mais indicado. (Avaliação aproximada: não há dados de capacidade térmica dos coolers.)",
                        "Consumo do processador: até " + power + " W · Altura do cooler: " + cooler.heightMm() + " mm"));
            }
        }

        PcCase pcCase = parts.pcCase();
        if (pcCase != null) {
            if (!cooler.isAirCooler()) {
                String radiator = cooler.radiatorSizeMm() == null ? "" : " de " + cooler.radiatorSizeMm() + " mm";
                findings.add(CompatibilityFinding.unverifiable("cooler.case", List.of(CPU_COOLER, CASE),
                        "Confirme o espaço para o radiador",
                        "Este é um cooler líquido. Nossa base de dados não informa se o gabinete aceita um radiador" + radiator
                                + " — confirme na página do fabricante do gabinete."));
            } else {
                switch (Fit.coolerHeightInCase(cooler, pcCase)) {
                    case NO -> findings.add(CompatibilityFinding.incompatible("cooler.case", List.of(CPU_COOLER, CASE),
                            "O cooler é alto demais para o gabinete",
                            "O cooler tem " + cooler.heightMm() + " mm de altura, e o gabinete comporta até " + pcCase.maxCoolerHeightMm() + " mm. A tampa lateral não fecharia.",
                            null));
                    case UNKNOWN -> findings.add(CompatibilityFinding.unverifiable("cooler.case", List.of(CPU_COOLER, CASE),
                            "Altura do cooler não confirmada",
                            "O fabricante do gabinete não informa a altura máxima de cooler (ou falta a altura do cooler), então não conseguimos confirmar que ele cabe."));
                    case YES -> {
                        int clearance = pcCase.maxCoolerHeightMm() - cooler.heightMm();
                        String detail = "Cooler: " + cooler.heightMm() + " mm · Limite do gabinete: " + pcCase.maxCoolerHeightMm() + " mm";
                        findings.add(clearance < Fit.TIGHT_CLEARANCE_MM
                                ? CompatibilityFinding.warning("cooler.case", List.of(CPU_COOLER, CASE),
                                        "O cooler cabe, mas bem justo",
                                        "Sobram só " + clearance + " mm entre o cooler e a tampa do gabinete.", detail)
                                : CompatibilityFinding.ok("cooler.case", List.of(CPU_COOLER, CASE),
                                        "O cooler cabe no gabinete",
                                        "Sobram " + clearance + " mm de folga até a tampa lateral.", detail));
                    }
                }
            }
        }
        return findings;
    }
}
