package app.platform.api;

import app.platform.builds.BuildAssembler;
import app.platform.catalog.Catalog;
import app.platform.catalog.CatalogHolder;
import app.platform.hardware.HardwareComponent;
import app.platform.pricing.Offer;
import app.platform.pricing.PriceService;
import app.platform.recommendation.BuildRequest;
import app.platform.recommendation.PerformanceEstimator;
import app.platform.recommendation.UpgradeAdvice;
import app.platform.recommendation.UpgradeAdvisor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
class UpgradeController {

    private final CatalogHolder catalogs;
    private final UpgradeAdvisor advisor;
    private final BuildAssembler assembler;
    private final PriceService prices;

    UpgradeController(CatalogHolder catalogs, UpgradeAdvisor advisor, BuildAssembler assembler, PriceService prices) {
        this.catalogs = catalogs;
        this.advisor = advisor;
        this.assembler = assembler;
        this.prices = prices;
    }

    /** "I have this PC and want it better": analysis of the current parts plus the best upgrade within budget. */
    @PostMapping("/upgrades")
    ApiViews.Upgrade advise(@Valid @RequestBody ApiRequests.Upgrade body) {
        Catalog catalog = catalogs.current();
        BuildRequest goals = body.goals().toDomain(body.currentComponentIds());
        UpgradeAdvice advice = advisor.advise(catalog, body.currentComponentIds(), goals, body.focus());
        Set<UUID> current = advice.current().all().stream().map(HardwareComponent::id).collect(Collectors.toSet());

        return new ApiViews.Upgrade(
                advice.assessment().stream().map(assessment -> new ApiViews.Assessment(
                        assessment.category().name(), assessment.category().label(),
                        assessment.component() == null ? null : summary(assessment.component()),
                        assessment.level().name(), assessment.title(), assessment.explanation())).toList(),
                advice.recommended() == null ? null : plan(catalog, goals, advice.recommended(), current),
                advice.alternatives().stream().map(plan -> plan(catalog, goals, plan, current)).toList(),
                advice.notes(),
                new ApiViews.Disclaimers(usesExamplePrices(advice) ? ViewMapper.PRICE_DISCLAIMER : null, PerformanceEstimator.DISCLAIMER));
    }

    private boolean usesExamplePrices(UpgradeAdvice advice) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.ofNullable(advice.recommended()), advice.alternatives().stream())
                .flatMap(plan -> plan.changes().stream())
                .anyMatch(change -> prices.bestOffer(change.part()).map(offer -> offer.kind() == Offer.Kind.EXAMPLE).orElse(false));
    }

    private ApiViews.Plan plan(Catalog catalog, BuildRequest goals, UpgradeAdvice.UpgradePlan plan, Set<UUID> current) {
        List<UUID> resultIds = plan.result().all().stream().map(HardwareComponent::id).toList();
        List<UUID> kept = resultIds.stream().filter(current::contains).toList();
        return new ApiViews.Plan(
                plan.kind().name(), plan.title(), plan.impact(), plan.costBrl(),
                plan.changes().stream().map(change -> new ApiViews.ChangeView(
                        change.category().name(), change.category().label(), change.role().name(),
                        change.replaces() == null ? null : summary(change.replaces()),
                        summary(change.part()),
                        ViewMapper.price(prices.bestOffer(change.part()).orElse(null)),
                        change.reason())).toList(),
                plan.result().all().stream().filter(part -> current.contains(part.id())).map(UpgradeController::summary).toList(),
                plan.dependencies(),
                ViewMapper.build(assembler.evaluate(catalog, goals, resultIds, Set.copyOf(kept))));
    }

    private static ApiViews.ComponentSummary summary(HardwareComponent component) {
        return new ApiViews.ComponentSummary(component.id(), component.name(), component.category().name(), component.category().label());
    }
}
