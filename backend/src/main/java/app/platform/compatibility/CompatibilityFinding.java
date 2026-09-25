package app.platform.compatibility;

import app.platform.hardware.ComponentCategory;

import java.util.List;

/**
 * Result of one check.
 *
 * @param title           short plain-language label, e.g. "O processador encaixa na placa-mãe"
 * @param explanation     plain-language explanation shown to the user
 * @param technicalDetail values that support the conclusion, for users who want the specifics
 * @param verified        false when the conclusion could not be confirmed with data (missing or discarded fields)
 */
public record CompatibilityFinding(
        String ruleId,
        CompatibilityStatus status,
        List<ComponentCategory> involves,
        String title,
        String explanation,
        String technicalDetail,
        boolean verified) {

    public CompatibilityFinding {
        involves = List.copyOf(involves);
    }

    static CompatibilityFinding ok(String ruleId, List<ComponentCategory> involves, String title, String explanation, String detail) {
        return new CompatibilityFinding(ruleId, CompatibilityStatus.OK, involves, title, explanation, detail, true);
    }

    static CompatibilityFinding warning(String ruleId, List<ComponentCategory> involves, String title, String explanation, String detail) {
        return new CompatibilityFinding(ruleId, CompatibilityStatus.WARNING, involves, title, explanation, detail, true);
    }

    static CompatibilityFinding unverifiable(String ruleId, List<ComponentCategory> involves, String title, String explanation) {
        return new CompatibilityFinding(ruleId, CompatibilityStatus.WARNING, involves, title, explanation, null, false);
    }

    static CompatibilityFinding incompatible(String ruleId, List<ComponentCategory> involves, String title, String explanation, String detail) {
        return new CompatibilityFinding(ruleId, CompatibilityStatus.INCOMPATIBLE, involves, title, explanation, detail, true);
    }
}
