package app.platform.builds;

import app.platform.catalog.Catalog;
import app.platform.compatibility.BuildParts;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.compatibility.CompatibilityReport;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.HardwareComponent;
import app.platform.pricing.Offer;
import app.platform.pricing.PriceService;
import app.platform.recommendation.Alternative;
import app.platform.recommendation.BuildRequest;
import app.platform.recommendation.ComponentExplainer;
import app.platform.recommendation.Recommendation;
import app.platform.recommendation.RecommendationEngine;
import app.platform.recommendation.RequirementAnalyzer;
import app.platform.recommendation.RequirementProfile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a set of parts into what the person sees: prices resolved server-side, compatibility,
 * explanations and alternatives. Used both for fresh recommendations and for re-evaluating
 * a build after the person swaps a part.
 */
public final class BuildAssembler {

    private final RecommendationEngine recommendations;
    private final CompatibilityEngine compatibility;
    private final PriceService prices;

    public BuildAssembler(RecommendationEngine recommendations, CompatibilityEngine compatibility, PriceService prices) {
        this.recommendations = recommendations;
        this.compatibility = compatibility;
        this.prices = prices;
    }

    public BuildResult recommend(Catalog catalog, BuildRequest request) {
        Recommendation recommendation = recommendations.recommend(catalog, request);
        return assemble(catalog, request, recommendation.profile(), recommendation.parts(), recommendation.ownedIds(),
                recommendation.notes(), true);
    }

    /**
     * Evaluates parts chosen or swapped by the person.
     *
     * @param request optional; when present, explanations and budget checks refer to it
     */
    public BuildResult evaluate(Catalog catalog, BuildRequest request, List<UUID> componentIds, Set<UUID> ownedIds) {
        List<HardwareComponent> components = componentIds.stream()
                .distinct()
                .map(id -> catalog.find(id).orElseThrow(() -> new IllegalArgumentException("Peça não encontrada: " + id)))
                .toList();
        BuildParts parts = BuildParts.of(components);
        RequirementProfile profile = request == null ? null : RequirementAnalyzer.analyze(request);
        return assemble(catalog, request, profile, parts, ownedIds, List.of(), false);
    }

    private BuildResult assemble(Catalog catalog, BuildRequest request, RequirementProfile profile, BuildParts parts,
                                 Set<UUID> ownedIds, List<String> notes, boolean engineChoice) {
        CompatibilityReport report = compatibility.check(parts);
        Map<ComponentCategory, List<Alternative>> alternatives = request == null
                ? Map.of()
                : recommendations.alternatives(catalog, parts, ownedIds, profile);

        List<BuildResult.Item> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean allPriced = true;
        boolean examples = false;
        for (HardwareComponent component : parts.all().stream().sorted(Comparator.comparing(HardwareComponent::category)).toList()) {
            boolean owned = ownedIds.contains(component.id());
            Offer offer = owned ? null : prices.bestOffer(component).orElse(null);
            if (!owned) {
                if (offer == null) {
                    allPriced = false;
                } else {
                    total = total.add(offer.priceBrl());
                    examples |= offer.kind() == Offer.Kind.EXAMPLE;
                }
            }
            items.add(new BuildResult.Item(component, owned, offer,
                    ComponentExplainer.explain(component, parts, profile, request, owned, engineChoice),
                    alternatives.getOrDefault(component.category(), List.of())));
        }
        Boolean withinBudget = request == null ? null : total.compareTo(request.budgetBrl()) <= 0;
        return new BuildResult(request, profile, items, report, total, withinBudget, allPriced, examples, notes, catalog.version());
    }
}
