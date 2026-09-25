package app.platform.compatibility;

import app.platform.hardware.ComponentCategory;
import app.platform.hardware.Cpu;
import app.platform.hardware.Motherboard;

import java.util.ArrayList;
import java.util.List;

import static app.platform.hardware.ComponentCategory.CPU;
import static app.platform.hardware.ComponentCategory.MOTHERBOARD;

final class CpuMotherboardRule implements CompatibilityRule {

    private static final List<ComponentCategory> INVOLVES = List.of(CPU, MOTHERBOARD);

    @Override
    public List<CompatibilityFinding> evaluate(BuildParts parts) {
        Cpu cpu = parts.cpu();
        Motherboard board = parts.motherboard();
        if (cpu == null || board == null) {
            return List.of();
        }
        List<CompatibilityFinding> findings = new ArrayList<>();
        String detail = "Encaixe do processador: " + cpu.socket() + " · Encaixe da placa-mãe: " + board.socket();

        switch (Fit.socket(cpu, board)) {
            case NO -> {
                findings.add(CompatibilityFinding.incompatible("cpu-board.socket", INVOLVES,
                        "O processador não encaixa na placa-mãe",
                        "O processador usa um encaixe diferente do que a placa-mãe tem.",
                        detail));
                return findings;
            }
            case UNKNOWN -> findings.add(CompatibilityFinding.unverifiable("cpu-board.socket", INVOLVES,
                    "Encaixe não confirmado",
                    "Não conseguimos confirmar o tipo de encaixe de uma das peças."));
            case YES -> findings.add(CompatibilityFinding.ok("cpu-board.socket", INVOLVES,
                    "O processador encaixa na placa-mãe",
                    "Os dois usam o mesmo tipo de encaixe.",
                    detail));
        }

        PlatformSupport.evaluate(cpu, board).ifPresent(outcome -> findings.add(new CompatibilityFinding(
                "cpu-board.generation", outcome.status(), INVOLVES,
                outcome.status() == CompatibilityStatus.INCOMPATIBLE ? "Geração incompatível" : "Pode precisar de atualização",
                outcome.explanation(), outcome.detail(), true)));

        if (Fit.cpuSupportsBoardMemory(cpu, board) == Fit.Verdict.NO) {
            findings.add(CompatibilityFinding.incompatible("cpu-board.memory", INVOLVES,
                    "Tipo de memória não suportado",
                    "A placa-mãe usa memória " + board.ramType() + ", que este processador não suporta.",
                    "Memórias suportadas pelo processador: " + String.join(", ", cpu.memoryTypes())));
        }
        return findings;
    }
}
