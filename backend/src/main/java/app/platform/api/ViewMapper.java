package app.platform.api;

import app.platform.builds.BuildResult;
import app.platform.catalog.CatalogVersion;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.compatibility.CompatibilityReport;
import app.platform.hardware.HardwareComponent;
import app.platform.infra.opendb.OpenDbRecordMapper;
import app.platform.infra.opendb.OpenDbSnapshot;
import app.platform.pricing.Offer;
import app.platform.recommendation.Alternative;
import app.platform.recommendation.BuildRequest;
import app.platform.recommendation.ComponentExplainer;
import app.platform.recommendation.PerformanceEstimator;
import app.platform.recommendation.RecommendationEngine;

import java.util.List;

/** Converts domain results into the public API documents. */
final class ViewMapper {

    static final String DATA_SOURCE_NAME = "BuildCores OpenDB";
    static final String PRICE_DISCLAIMER =
            "Os preços desta versão são fictícios, usados apenas para demonstrar o funcionamento. Ainda não consultamos lojas reais.";

    private ViewMapper() {
    }

    static ApiViews.Build build(BuildResult result) {
        CatalogVersion version = result.catalogVersion();
        List<ApiViews.Item> items = result.items().stream().map(item -> item(item, version)).toList();
        BuildRequest request = result.request();
        return new ApiViews.Build(
                request == null ? null : needs(request),
                result.profile() == null ? List.of() : result.profile().reasons(),
                items,
                compatibility(result.compatibility()),
                new ApiViews.Totals(result.totalBrl(), request == null ? null : request.budgetBrl(), result.withinBudget(),
                        result.allPriced(), result.pricesAreExamples()),
                result.notes(),
                dataSource(version),
                new ApiViews.Disclaimers(result.pricesAreExamples() ? PRICE_DISCLAIMER : null, PerformanceEstimator.DISCLAIMER),
                new ApiViews.Engines(RecommendationEngine.VERSION, CompatibilityEngine.VERSION));
    }

    static ApiViews.NeedsView needs(BuildRequest request) {
        return new ApiViews.NeedsView(
                request.budgetBrl(),
                request.useCases().stream().sorted().map(use -> new ApiViews.Labeled(use.name(), use.label())).toList(),
                request.primaryUse() == null ? null : new ApiViews.Labeled(request.primaryUse().name(), request.primaryUse().label()),
                new ApiViews.Labeled(request.resolution().name(), request.resolution().label()),
                request.ownedComponentIds());
    }

    static ApiViews.Component component(HardwareComponent component, CatalogVersion version) {
        var info = component.info();
        String recordUrl = OpenDbRecordMapper.SOURCE.equals(info.source().source()) && version.sourceUrl() != null
                ? OpenDbSnapshot.recordUrl(version.sourceUrl(), version.sourceVersion(), info.category(), info.source().externalId())
                : null;
        return new ApiViews.Component(
                info.id(), info.name(), info.manufacturer(), info.category().name(), info.category().label(), info.releaseYear(),
                new ApiViews.Source(DATA_SOURCE_NAME, info.source().externalId(), recordUrl, OpenDbSnapshot.LICENSE_NAME),
                new ApiViews.Quality(info.quality().score(), info.quality().issues().stream()
                        .map(issue -> new ApiViews.Issue(issue.field(), issue.kind().name(), issue.detail()))
                        .toList()));
    }

    static ApiViews.Price price(Offer offer) {
        if (offer == null) {
            return null;
        }
        return new ApiViews.Price(offer.priceBrl(), offer.kind().name(), offer.kind() == Offer.Kind.EXAMPLE,
                offer.storeName(), offer.url(), offer.observedAt());
    }

    static List<ApiViews.Spec> specs(List<ComponentExplainer.Spec> specs) {
        return specs.stream().map(spec -> new ApiViews.Spec(spec.label(), spec.value())).toList();
    }

    static ApiViews.DataSource dataSource(CatalogVersion version) {
        return new ApiViews.DataSource(DATA_SOURCE_NAME, version.sourceVersion(), version.sourceUrl(),
                OpenDbSnapshot.LICENSE_NAME, OpenDbSnapshot.LICENSE_URL, version.ingestedAt());
    }

    private static ApiViews.Item item(BuildResult.Item item, CatalogVersion version) {
        HardwareComponent component = item.component();
        ComponentExplainer.Explanation explanation = item.explanation();
        return new ApiViews.Item(
                component.category().name(),
                component.category().label(),
                item.owned(),
                component(component, version),
                price(item.offer()),
                new ApiViews.Explanation(explanation.whatItIs(), explanation.whyItMatters(), explanation.reason()),
                specs(explanation.specs()),
                item.alternatives().stream().map(ViewMapper::alternative).toList());
    }

    private static ApiViews.AlternativeView alternative(Alternative alternative) {
        HardwareComponent component = alternative.component();
        return new ApiViews.AlternativeView(
                new ApiViews.ComponentSummary(component.id(), component.name(), component.category().name(), component.category().label()),
                alternative.priceBrl(), alternative.priceDeltaBrl(), alternative.direction().name(), alternative.impact());
    }

    private static ApiViews.Compatibility compatibility(CompatibilityReport report) {
        return new ApiViews.Compatibility(
                report.overall().name(),
                report.summary(),
                report.findings().stream().map(finding -> new ApiViews.Finding(
                        finding.ruleId(), finding.status().name(),
                        finding.involves().stream().map(category -> category.name()).toList(),
                        finding.title(), finding.explanation(), finding.technicalDetail(), finding.verified())).toList(),
                new ApiViews.Power(report.power().estimatedLoadWatts(), report.power().recommendedPsuWatts(), report.power().complete()));
    }
}
