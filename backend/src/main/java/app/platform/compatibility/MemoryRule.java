package app.platform.compatibility;

import app.platform.hardware.ComponentCategory;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;

import java.util.ArrayList;
import java.util.List;

import static app.platform.hardware.ComponentCategory.MEMORY;
import static app.platform.hardware.ComponentCategory.MOTHERBOARD;

final class MemoryRule implements CompatibilityRule {

    private static final List<ComponentCategory> INVOLVES = List.of(MEMORY, MOTHERBOARD);

    @Override
    public List<CompatibilityFinding> evaluate(BuildParts parts) {
        Memory memory = parts.memory();
        if (memory == null) {
            return List.of();
        }
        List<CompatibilityFinding> findings = new ArrayList<>();

        if (Fit.memoryIsDesktopModule(memory) == Fit.Verdict.NO) {
            findings.add(CompatibilityFinding.incompatible("memory.form", List.of(MEMORY),
                    "Memória de notebook",
                    "Essa memória é do formato usado em notebooks e não encaixa em placas-mãe de computador de mesa.",
                    "Formato: " + memory.formFactor()));
        }

        Motherboard board = parts.motherboard();
        if (board == null) {
            return findings;
        }
        switch (Fit.memoryType(memory, board)) {
            case NO -> findings.add(CompatibilityFinding.incompatible("memory.type", INVOLVES,
                    "A memória não é do tipo da placa-mãe",
                    "A placa-mãe aceita memória " + board.ramType() + ", mas a escolhida é " + memory.ramType() + ". Os encaixes são diferentes.",
                    "Memória: " + memory.ramType() + " · Placa-mãe: " + board.ramType()));
            case UNKNOWN -> findings.add(CompatibilityFinding.unverifiable("memory.type", INVOLVES,
                    "Tipo de memória não confirmado",
                    "Não conseguimos confirmar se a memória é do mesmo tipo que a placa-mãe aceita."));
            case YES -> findings.add(CompatibilityFinding.ok("memory.type", INVOLVES,
                    "A memória é do tipo certo",
                    "A placa-mãe aceita memória " + board.ramType() + ", igual à escolhida.",
                    null));
        }

        if (Fit.memorySlots(memory, board) == Fit.Verdict.NO) {
            findings.add(CompatibilityFinding.incompatible("memory.slots", INVOLVES,
                    "Pentes demais para a placa-mãe",
                    "O kit tem " + memory.modules() + " pentes, mas a placa-mãe só tem " + board.memorySlots() + " encaixes de memória.",
                    null));
        }
        if (Fit.memoryCapacity(memory, board) == Fit.Verdict.NO) {
            findings.add(CompatibilityFinding.incompatible("memory.capacity", INVOLVES,
                    "Memória acima do limite da placa-mãe",
                    "A placa-mãe suporta até " + board.maxMemoryGb() + " GB, e o kit tem " + memory.totalCapacityGb() + " GB.",
                    null));
        }
        if (memory.modules() != null && memory.modules() == 1) {
            findings.add(CompatibilityFinding.warning("memory.channels", List.of(MEMORY),
                    "Só um pente de memória",
                    "Funciona, mas dois pentes iguais deixam a memória mais rápida (modo dual channel). Em jogos isso pode fazer diferença.",
                    null));
        }
        return findings;
    }
}
