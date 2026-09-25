package app.platform.compatibility;

import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;

import java.util.ArrayList;
import java.util.List;

import static app.platform.hardware.ComponentCategory.CASE;
import static app.platform.hardware.ComponentCategory.MOTHERBOARD;
import static app.platform.hardware.ComponentCategory.POWER_SUPPLY;

final class CaseRule implements CompatibilityRule {

    @Override
    public List<CompatibilityFinding> evaluate(BuildParts parts) {
        PcCase pcCase = parts.pcCase();
        if (pcCase == null) {
            return List.of();
        }
        List<CompatibilityFinding> findings = new ArrayList<>();

        Motherboard board = parts.motherboard();
        if (board != null) {
            switch (Fit.motherboardInCase(board, pcCase)) {
                case NO -> findings.add(CompatibilityFinding.incompatible("case.motherboard", List.of(MOTHERBOARD, CASE),
                        "A placa-mãe não cabe no gabinete",
                        "A placa-mãe é do tamanho " + board.formFactor() + ", e o gabinete aceita apenas: "
                                + String.join(", ", pcCase.supportedMotherboardFormFactors()) + ".",
                        null));
                case UNKNOWN -> findings.add(CompatibilityFinding.unverifiable("case.motherboard", List.of(MOTHERBOARD, CASE),
                        "Tamanho da placa-mãe não confirmado",
                        "Não conseguimos confirmar se o gabinete aceita o tamanho desta placa-mãe."));
                case YES -> findings.add(CompatibilityFinding.ok("case.motherboard", List.of(MOTHERBOARD, CASE),
                        "A placa-mãe cabe no gabinete",
                        "O gabinete aceita placas-mãe do tamanho " + board.formFactor() + ".",
                        null));
            }
        }

        PowerSupply psu = parts.powerSupply();
        if (psu != null) {
            switch (Fit.psuFormFactorInCase(psu, pcCase)) {
                case NO -> findings.add(CompatibilityFinding.incompatible("case.psu", List.of(POWER_SUPPLY, CASE),
                        "A fonte não é do formato do gabinete",
                        "A fonte é do formato " + psu.formFactor() + ", e o gabinete aceita: "
                                + String.join(", ", pcCase.supportedPsuFormFactors()) + ".",
                        null));
                case UNKNOWN -> findings.add(CompatibilityFinding.unverifiable("case.psu", List.of(POWER_SUPPLY, CASE),
                        "Formato da fonte não confirmado",
                        "Não conseguimos confirmar se este gabinete aceita uma fonte do formato " + psu.formFactor() + "."));
                case YES -> {
                    if (Fit.psuLengthInCase(psu, pcCase) == Fit.Verdict.NO) {
                        findings.add(CompatibilityFinding.incompatible("case.psu", List.of(POWER_SUPPLY, CASE),
                                "A fonte é comprida demais",
                                "A fonte tem " + psu.lengthMm() + " mm, e o gabinete comporta até " + pcCase.maxPsuLengthMm() + " mm.",
                                null));
                    } else {
                        findings.add(CompatibilityFinding.ok("case.psu", List.of(POWER_SUPPLY, CASE),
                                "A fonte cabe no gabinete",
                                "O gabinete aceita fontes do formato " + psu.formFactor() + ".",
                                null));
                    }
                }
            }
        }
        return findings;
    }
}
