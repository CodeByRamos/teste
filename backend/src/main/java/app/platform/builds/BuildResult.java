package app.platform.builds;

import app.platform.catalog.CatalogVersion;
import app.platform.compatibility.CompatibilityReport;
import app.platform.hardware.HardwareComponent;
import app.platform.pricing.Offer;
import app.platform.recommendation.Alternative;
import app.platform.recommendation.BuildRequest;
import app.platform.recommendation.ComponentExplainer;
import app.platform.recommendation.FutureOutlook;
import app.platform.recommendation.RequirementProfile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything shown for a build: parts with prices and explanations, compatibility, and totals.
 *
 * @param request       {@code null} when the person assembled the parts themselves
 * @param withinBudget  {@code null} when there is no budget to compare with
 * @param future        room to evolve part by part; {@code null} without a motherboard or when the build does not work
 * @param allPriced     false when some part to be bought has no price from any provider
 */
public record BuildResult(
        BuildRequest request,
        RequirementProfile profile,
        List<Item> items,
        CompatibilityReport compatibility,
        FutureOutlook future,
        BigDecimal totalBrl,
        Boolean withinBudget,
        boolean allPriced,
        boolean pricesAreExamples,
        List<String> notes,
        CatalogVersion catalogVersion) {

    public BuildResult {
        items = List.copyOf(items);
        notes = List.copyOf(notes);
    }

    /** One part of the build. {@code offer} is {@code null} for owned parts and parts without a price. */
    public record Item(
            HardwareComponent component,
            boolean owned,
            Offer offer,
            ComponentExplainer.Explanation explanation,
            List<Alternative> alternatives) {

        public Item {
            alternatives = List.copyOf(alternatives);
        }
    }
}
