package app.platform.compatibility;

import app.platform.hardware.Motherboard;
import app.platform.hardware.Storage;

import java.util.ArrayList;
import java.util.List;

import static app.platform.hardware.ComponentCategory.MOTHERBOARD;
import static app.platform.hardware.ComponentCategory.STORAGE;

final class StorageRule implements CompatibilityRule {

    @Override
    public List<CompatibilityFinding> evaluate(BuildParts parts) {
        Motherboard board = parts.motherboard();
        if (board == null || parts.storage().isEmpty()) {
            return List.of();
        }
        List<CompatibilityFinding> findings = new ArrayList<>();
        List<Storage> m2Drives = parts.storage().stream().filter(Storage::isM2).toList();
        List<Storage> sataDrives = parts.storage().stream().filter(Storage::usesSataPort).toList();

        for (Storage drive : m2Drives) {
            switch (Fit.m2DriveOnBoard(drive, board)) {
                case NO -> findings.add(CompatibilityFinding.incompatible("storage.m2", List.of(STORAGE, MOTHERBOARD),
                        "O SSD não encaixa na placa-mãe",
                        "A placa-mãe não tem um encaixe M.2 compatível com este SSD (" + drive.formFactor() + ", " + drive.interfaceName() + ").",
                        null));
                case UNKNOWN -> findings.add(CompatibilityFinding.unverifiable("storage.m2", List.of(STORAGE, MOTHERBOARD),
                        "Encaixe do SSD não confirmado",
                        "Não conseguimos confirmar os encaixes M.2 desta placa-mãe."));
                case YES -> findings.add(CompatibilityFinding.ok("storage.m2", List.of(STORAGE, MOTHERBOARD),
                        "O SSD encaixa na placa-mãe",
                        "A placa-mãe tem encaixe M.2 compatível com este SSD.",
                        null));
            }
        }
        if (board.m2Slots() != null && m2Drives.size() > board.driveM2Slots().size()) {
            findings.add(CompatibilityFinding.incompatible("storage.m2-count", List.of(STORAGE, MOTHERBOARD),
                    "SSDs M.2 demais",
                    "Foram escolhidos " + m2Drives.size() + " SSDs M.2, mas a placa-mãe tem " + board.driveM2Slots().size() + " encaixes M.2.",
                    null));
        }
        if (!sataDrives.isEmpty()) {
            if (board.sataPorts() == null) {
                findings.add(CompatibilityFinding.unverifiable("storage.sata", List.of(STORAGE, MOTHERBOARD),
                        "Portas SATA não confirmadas",
                        "Não conseguimos confirmar quantas portas SATA esta placa-mãe tem."));
            } else if (sataDrives.size() > board.sataPorts()) {
                findings.add(CompatibilityFinding.incompatible("storage.sata", List.of(STORAGE, MOTHERBOARD),
                        "Portas SATA insuficientes",
                        "Há " + sataDrives.size() + " discos SATA e a placa-mãe tem " + board.sataPorts() + " portas.",
                        null));
            } else {
                findings.add(CompatibilityFinding.ok("storage.sata", List.of(STORAGE, MOTHERBOARD),
                        "Os discos SATA têm onde ligar",
                        "A placa-mãe tem " + board.sataPorts() + " portas SATA.",
                        null));
            }
        }
        return findings;
    }
}
