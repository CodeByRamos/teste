package app.platform.recommendation;

import app.platform.catalog.Catalog;
import app.platform.compatibility.BuildParts;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.HardwareComponent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * "What if I spend a bit less or a bit more?": runs the same engine at neighboring budgets and says, in plain
 * language, how performance and parts change. Also detects when spending more barely helps for these uses.
 */
public final class BudgetExplorer {

    /** Below this gain, more money is not worth it for these uses. */
    static final double MEANINGFUL_GAIN = 1.05;

    private final RecommendationEngine engine;

    public BudgetExplorer(RecommendationEngine engine) {
        this.engine = engine;
    }

    /**
     * @param budgetBrl       the budget this option was computed for
     * @param totalBrl        what the parts cost; {@code null} when no complete build fits
     * @param performanceText e.g. "cerca de 30% a mais de desempenho em jogos"
     * @param changes         parts that differ from the current build
     * @param notWorthIt      spending more barely changes performance for these uses
     */
    public record BudgetOption(BigDecimal budgetBrl, BigDecimal totalBrl, boolean feasible, String performanceText,
                               List<PartChange> changes, boolean notWorthIt) {
        public BudgetOption {
            changes = List.copyOf(changes);
        }
    }

    /** {@code from} is null when the part is added, {@code to} when it is dropped. */
    public record PartChange(ComponentCategory category, String from, String to) {
    }

    public List<BudgetOption> explore(Catalog catalog, BuildRequest request) {
        Recommendation current = engine.recommend(catalog, request);
        BigDecimal budget = request.budgetBrl();
        BigDecimal step = step(budget);
        List<BudgetOption> options = new ArrayList<>();
        BigDecimal lower = budget.subtract(step);
        if (lower.compareTo(BuildRequest.MIN_BUDGET) >= 0) {
            options.add(option(catalog, request, current, lower, false));
        }
        BigDecimal higher = budget.add(step);
        if (higher.compareTo(BuildRequest.MAX_BUDGET) <= 0) {
            options.add(option(catalog, request, current, higher, true));
        }
        return options;
    }

    /** About 20% of the budget, in steps people think in: R$ 500 to R$ 5.000. */
    static BigDecimal step(BigDecimal budget) {
        BigDecimal fifth = budget.multiply(new BigDecimal("0.2"));
        BigDecimal rounded = fifth.divide(new BigDecimal("500"), 0, RoundingMode.HALF_UP).multiply(new BigDecimal("500"));
        return rounded.max(new BigDecimal("500")).min(new BigDecimal("5000"));
    }

    private BudgetOption option(Catalog catalog, BuildRequest request, Recommendation current, BigDecimal budget, boolean higher) {
        Recommendation other;
        try {
            other = engine.recommend(catalog, request.withBudget(budget));
        } catch (NoFeasibleBuildException e) {
            return new BudgetOption(budget, null, false, null, List.of(), false);
        }
        if (!other.withinBudget()) {
            return new BudgetOption(budget, null, false, null, List.of(), false);
        }
        double ratio = performanceRatio(current.profile(), current.parts(), other.parts());
        List<PartChange> changes = changes(current.parts(), other.parts());
        boolean notWorthIt = higher && ratio < MEANINGFUL_GAIN;
        return new BudgetOption(budget, other.totalBrl(), true, performanceText(current.profile(), current.parts(), other.parts(), ratio),
                changes, notWorthIt);
    }

    /** The metric that matters most for these uses: graphics for games, multi-core otherwise. */
    private static boolean graphicsMetric(RequirementProfile profile, BuildParts a, BuildParts b) {
        return profile.gpuWeight() >= profile.cpuMultiThreadWeight() && a.gpu() != null && b.gpu() != null;
    }

    static double performanceRatio(RequirementProfile profile, BuildParts current, BuildParts other) {
        boolean graphics = graphicsMetric(profile, current, other);
        Double now = graphics ? PerformanceEstimator.gpuScore(current.gpu()) : PerformanceEstimator.cpuMultiThreadScore(current.cpu());
        Double then = graphics ? PerformanceEstimator.gpuScore(other.gpu()) : PerformanceEstimator.cpuMultiThreadScore(other.cpu());
        if (now == null || then == null || now <= 0) {
            return 1;
        }
        return then / now;
    }

    private static String performanceText(RequirementProfile profile, BuildParts current, BuildParts other, double ratio) {
        String metric = graphicsMetric(profile, current, other)
                ? (profile.gamingFocused() ? "desempenho em jogos" : "desempenho gráfico")
                : "desempenho com vários programas";
        if (ratio >= MEANINGFUL_GAIN) {
            return FutureOutlookAnalyzer.gainText(ratio).replace("desempenho", metric);
        }
        if (ratio <= 1 / MEANINGFUL_GAIN) {
            long percent = Math.max(5, Math.round((1 - ratio) * 20) * 5);
            return "cerca de " + percent + "% a menos de " + metric;
        }
        return "praticamente o mesmo " + metric;
    }

    static List<PartChange> changes(BuildParts current, BuildParts other) {
        List<PartChange> changes = new ArrayList<>();
        for (ComponentCategory category : ComponentCategory.values()) {
            if (category == ComponentCategory.STORAGE) {
                continue;
            }
            HardwareComponent before = single(current, category);
            HardwareComponent after = single(other, category);
            if (!Objects.equals(id(before), id(after))) {
                changes.add(new PartChange(category, before == null ? null : before.name(), after == null ? null : after.name()));
            }
        }
        List<String> drivesBefore = current.storage().stream().map(HardwareComponent::name).sorted().toList();
        List<String> drivesAfter = other.storage().stream().map(HardwareComponent::name).sorted().toList();
        if (!drivesBefore.equals(drivesAfter)) {
            changes.add(new PartChange(ComponentCategory.STORAGE, drivesBefore.isEmpty() ? null : String.join(" + ", drivesBefore),
                    drivesAfter.isEmpty() ? null : String.join(" + ", drivesAfter)));
        }
        changes.sort(Comparator.comparing(PartChange::category));
        return changes;
    }

    private static HardwareComponent single(BuildParts parts, ComponentCategory category) {
        return parts.all().stream().filter(part -> part.category() == category).findFirst().orElse(null);
    }

    private static Object id(HardwareComponent component) {
        return component == null ? null : component.id();
    }
}
