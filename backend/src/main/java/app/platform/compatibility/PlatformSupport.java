package app.platform.compatibility;

import app.platform.hardware.Cpu;
import app.platform.hardware.Hardware;
import app.platform.hardware.Motherboard;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processor-generation rules that a matching socket name does not capture. The source data has no
 * BIOS or CPU-support lists, so these are maintained here from vendor platform documentation.
 */
final class PlatformSupport {

    /** Desktop chipsets only; workstation "C" chipsets do not follow the same generation numbering. */
    private static final Pattern CHIPSET_MODEL = Pattern.compile("\\b([ABHQZX])(\\d)(\\d{2})[A-Z]?\\b");

    private static final Set<String> LGA1151_FIRST_GEN = Set.of("Skylake", "Kaby Lake");
    private static final Set<String> LGA1151_SECOND_GEN = Set.of("Coffee Lake", "Coffee Lake Refresh");
    private static final Set<String> RAPTOR_LAKE = Set.of("Raptor Lake", "Raptor Lake Refresh");

    record Outcome(CompatibilityStatus status, String explanation, String detail) {
    }

    private PlatformSupport() {
    }

    /** Chipset "series" digit: "Intel Z390" → 3, "AMD B650" → 6. */
    static Integer chipsetSeries(String chipset) {
        if (chipset == null) {
            return null;
        }
        Matcher matcher = CHIPSET_MODEL.matcher(chipset);
        return matcher.find() ? Integer.valueOf(matcher.group(2)) : null;
    }

    static Optional<Outcome> evaluate(Cpu cpu, Motherboard board) {
        String socket = Hardware.normalizeSocket(cpu.socket());
        String arch = cpu.microarchitecture();
        Integer series = chipsetSeries(board.chipset());
        if (socket == null || arch == null || series == null) {
            return Optional.empty();
        }
        String detail = "Processador: " + arch + " · Chipset: " + board.chipset();
        return switch (socket) {
            case "LGA 1151" -> lga1151(arch, series, detail);
            case "AM4" -> am4(arch, series, detail);
            case "LGA 1700" -> RAPTOR_LAKE.contains(arch) && series == 6
                    ? Optional.of(biosUpdate(detail))
                    : Optional.empty();
            case "AM5" -> "Zen 5".equals(arch) && series == 6
                    ? Optional.of(biosUpdate(detail))
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    private static Optional<Outcome> lga1151(String arch, int series, String detail) {
        boolean firstGenBoard = series == 1 || series == 2;
        boolean secondGenBoard = series == 3;
        if (LGA1151_FIRST_GEN.contains(arch) && secondGenBoard || LGA1151_SECOND_GEN.contains(arch) && firstGenBoard) {
            return Optional.of(new Outcome(CompatibilityStatus.INCOMPATIBLE,
                    "O encaixe tem o mesmo nome (LGA 1151), mas esta placa-mãe é de outra geração e não aceita este processador.",
                    detail));
        }
        return Optional.empty();
    }

    private static Optional<Outcome> am4(String arch, int series, String detail) {
        boolean firstZen = "Zen".equals(arch) || "Zen+".equals(arch);
        if (firstZen && series == 5) {
            return Optional.of(new Outcome(CompatibilityStatus.INCOMPATIBLE,
                    "Placas-mãe da série 500 (como B550 e A520) não têm suporte oficial a processadores Ryzen dessa geração.",
                    detail));
        }
        if ("Zen 3".equals(arch) && (series == 3 || series == 4) || "Zen 2".equals(arch) && series == 3) {
            return Optional.of(biosUpdate(detail));
        }
        return Optional.empty();
    }

    private static Outcome biosUpdate(String detail) {
        return new Outcome(CompatibilityStatus.WARNING,
                "Essa placa-mãe pode precisar de uma atualização de BIOS (o software interno da placa) para reconhecer este processador. "
                        + "Muitas já saem de fábrica atualizadas, mas vale confirmar com a loja antes de comprar.",
                detail);
    }
}
