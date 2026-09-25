package app.platform.recommendation;

import app.platform.compatibility.BuildParts;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.HardwareComponent;

import java.math.BigDecimal;
import java.util.List;

/**
 * Analysis of an existing PC and what to change to improve it for the person's goals.
 *
 * @param recommended {@code null} when no upgrade within budget brings a meaningful gain
 */
public record UpgradeAdvice(
        BuildParts current,
        RequirementProfile profile,
        List<PartAssessment> assessment,
        UpgradePlan recommended,
        List<UpgradePlan> alternatives,
        List<String> notes) {

    public UpgradeAdvice {
        assessment = List.copyOf(assessment);
        alternatives = List.copyOf(alternatives);
        notes = List.copyOf(notes);
    }

    /** How one current part serves the person's goals. {@code component} is null when the part is absent. */
    public record PartAssessment(ComponentCategory category, HardwareComponent component, Level level, String title, String explanation) {
    }

    public enum Level {
        GOOD, ENOUGH, WEAK, BOTTLENECK
    }

    /**
     * A coherent set of changes. {@code result} is the whole PC after the upgrade and has been verified by the
     * compatibility engine.
     *
     * @param dependencies plain-language chain of consequences ("placa nova → consome mais → a fonte …")
     * @param gain         relative improvement score used for ranking (not shown to users as a number)
     */
    public record UpgradePlan(
            Kind kind,
            String title,
            String impact,
            List<Change> changes,
            BuildParts result,
            BigDecimal costBrl,
            double gain,
            List<String> dependencies) {

        public UpgradePlan {
            changes = List.copyOf(changes);
            dependencies = List.copyOf(dependencies);
        }

        public enum Kind {
            GPU, CPU, PLATFORM, MEMORY, STORAGE, COMBINED
        }
    }

    /**
     * One part to buy. {@code replaces} is null when the part is added rather than swapped.
     *
     * @param role MAIN for what the person gains from, REQUIRED for what the main change forces
     */
    public record Change(ComponentCategory category, HardwareComponent replaces, HardwareComponent part, Role role,
                         BigDecimal priceBrl, String reason) {

        public enum Role {
            MAIN, REQUIRED
        }
    }
}
