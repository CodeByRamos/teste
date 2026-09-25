package app.platform.api;

import app.platform.visualization.ModelSpec;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response documents. These are the public contract consumed by the web app. */
final class ApiViews {

    private ApiViews() {
    }

    record Build(
            NeedsView needs,
            List<String> requirements,
            List<Item> items,
            Compatibility compatibility,
            Future future,
            Totals totals,
            List<String> notes,
            DataSource dataSource,
            Disclaimers disclaimers,
            Engines engines) {
    }

    record NeedsView(BigDecimal budgetBrl, List<Labeled> useCases, Labeled primaryUse, Labeled resolution, List<UUID> ownedComponentIds) {
    }

    record Labeled(String value, String label) {
    }

    record Item(
            String category,
            String categoryLabel,
            boolean owned,
            Component component,
            Price price,
            Explanation explanation,
            List<Spec> specs,
            List<AlternativeView> alternatives,
            /* 3D models that draw this part (an AIO is radiator + pump); empty when none applies. */
            List<ModelSpec> models) {
    }

    record Component(
            UUID id,
            String name,
            String manufacturer,
            String category,
            String categoryLabel,
            Integer releaseYear,
            Source source,
            Quality quality) {
    }

    record Source(String name, String externalId, String recordUrl, String license) {
    }

    record Quality(double score, List<Issue> issues) {
    }

    record Issue(String field, String kind, String detail) {
    }

    /** {@code url} is null unless a real store provided it. */
    record Price(BigDecimal amountBrl, String kind, boolean isExample, String storeName, String url, Instant observedAt) {
    }

    record Explanation(String whatItIs, String whyItMatters, String reason) {
    }

    record Spec(String label, String value) {
    }

    record AlternativeView(ComponentSummary component, BigDecimal priceBrl, BigDecimal priceDeltaBrl, String direction, String impact) {
    }

    record ComponentSummary(UUID id, String name, String category, String categoryLabel) {
    }

    record Compatibility(String overall, String summary, List<Finding> findings, Power power) {
    }

    record Finding(String ruleId, String status, List<String> involves, String title, String explanation, String technicalDetail, boolean verified) {
    }

    record Future(String summary, List<FutureAspect> aspects, String disclaimer) {
    }

    record FutureAspect(String id, String title, String level, String headline, String explanation, String technicalDetail, List<String> involves) {
    }

    record Power(int estimatedLoadWatts, int recommendedPsuWatts, boolean complete) {
    }

    record Totals(BigDecimal totalBrl, BigDecimal budgetBrl, Boolean withinBudget, boolean allPriced, boolean pricesAreExamples) {
    }

    record DataSource(String name, String version, String url, String license, String licenseUrl, Instant ingestedAt) {
    }

    record Disclaimers(String prices, String performance) {
    }

    record Engines(String recommendation, String compatibility) {
    }

    record SearchResult(
            UUID id,
            String name,
            String manufacturer,
            String category,
            String categoryLabel,
            Integer releaseYear,
            Price price,
            List<Spec> highlights,
            double qualityScore) {
    }

    record Interpretation(
            BigDecimal budgetBrl,
            List<Labeled> useCases,
            Labeled resolution,
            boolean mentionsOwnedParts,
            List<String> understood,
            List<String> questions) {
    }

    record Saved(UUID id) {
    }

    record Options(List<Option> useCases, List<Labeled> resolutions, List<Labeled> categories, BigDecimal minBudgetBrl, BigDecimal maxBudgetBrl) {
    }

    record Option(String value, String label, String description) {
    }

    record Upgrade(List<Assessment> assessment, Plan recommended, List<Plan> alternatives, List<String> notes, Disclaimers disclaimers) {
    }

    record Assessment(String category, String categoryLabel, ComponentSummary component, String level, String title, String explanation) {
    }

    record Plan(String kind, String title, String impact, BigDecimal costBrl, List<ChangeView> changes,
                List<ComponentSummary> kept, List<String> dependencies, Build after) {
    }

    record ChangeView(String category, String categoryLabel, String role, ComponentSummary replaces, ComponentSummary part,
                      Price price, String reason) {
    }
}
