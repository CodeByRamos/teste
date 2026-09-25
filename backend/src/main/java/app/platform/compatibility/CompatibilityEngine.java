package app.platform.compatibility;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether parts work together using structured data and explicit rules only.
 * Language models may explain a report, never produce one.
 */
public final class CompatibilityEngine {

    public static final String VERSION = "compat-v1";

    private final List<CompatibilityRule> rules;

    public CompatibilityEngine(List<CompatibilityRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static CompatibilityEngine withDefaultRules() {
        // Specific pairwise problems first: they explain a failure better than a generic missing part.
        return new CompatibilityEngine(List.of(
                new CpuMotherboardRule(),
                new MemoryRule(),
                new CoolerRule(),
                new GpuRule(),
                new PowerRule(),
                new CaseRule(),
                new StorageRule(),
                new EssentialPartsRule()));
    }

    public CompatibilityReport check(BuildParts parts) {
        List<CompatibilityFinding> findings = new ArrayList<>();
        for (CompatibilityRule rule : rules) {
            findings.addAll(rule.evaluate(parts));
        }
        return CompatibilityReport.of(findings, PowerEstimator.estimate(parts));
    }
}
