package app.platform.compatibility;

import java.util.Comparator;
import java.util.List;

public record CompatibilityReport(
        CompatibilityStatus overall,
        String summary,
        List<CompatibilityFinding> findings,
        PowerEstimate power) {

    public CompatibilityReport {
        findings = List.copyOf(findings);
    }

    static CompatibilityReport of(List<CompatibilityFinding> findings, PowerEstimate power) {
        CompatibilityStatus overall = findings.stream()
                .map(CompatibilityFinding::status)
                .reduce(CompatibilityStatus.OK, CompatibilityStatus::worst);
        List<CompatibilityFinding> ordered = findings.stream()
                .sorted(Comparator.comparing((CompatibilityFinding f) -> f.status().ordinal()).reversed())
                .toList();
        return new CompatibilityReport(overall, summarize(overall, ordered), ordered, power);
    }

    public boolean isBuildable() {
        return overall != CompatibilityStatus.INCOMPATIBLE;
    }

    private static String summarize(CompatibilityStatus overall, List<CompatibilityFinding> ordered) {
        return switch (overall) {
            case OK -> "Tudo certo. As peças são compatíveis entre si.";
            case WARNING -> "Essa configuração funciona, mas existe um ponto que você deveria considerar.";
            case INCOMPATIBLE -> "Essa configuração não funciona porque " + lowerFirst(ordered.getFirst().explanation());
        };
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
