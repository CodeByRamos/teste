package app.platform.recommendation;

import app.platform.Fixtures;
import app.platform.catalog.Catalog;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.compatibility.CompatibilityStatus;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.Gpu;
import app.platform.infra.pricing.ExamplePriceProvider;
import app.platform.pricing.PriceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationEngineTest {

    private final CompatibilityEngine compatibility = CompatibilityEngine.withDefaultRules();
    private final RecommendationEngine engine =
            new RecommendationEngine(new PriceService(List.of(new ExamplePriceProvider())), compatibility);
    private final Catalog catalog = Fixtures.catalog();

    @Test
    void buildsACompleteCompatibleGamingPcFromRealRecords() {
        Recommendation recommendation = engine.recommend(catalog,
                new BuildRequest(new BigDecimal("30000"), Set.of(UseCase.GAMING_AAA), null, null, List.of()));

        assertThat(recommendation.withinBudget()).isTrue();
        assertThat(recommendation.parts().all()).hasSize(8);
        assertThat(compatibility.check(recommendation.parts()).overall()).isNotEqualTo(CompatibilityStatus.INCOMPATIBLE);
        assertThat(recommendation.totalBrl()).isLessThanOrEqualTo(new BigDecimal("30000"));
    }

    @Test
    void reportsHonestlyWhenBudgetIsTooLow() {
        Recommendation recommendation = engine.recommend(catalog,
                new BuildRequest(new BigDecimal("1500"), Set.of(UseCase.GAMING_AAA), null, null, List.of()));

        assertThat(recommendation.withinBudget()).isFalse();
        assertThat(recommendation.notes()).anyMatch(note -> note.contains("não encontramos"));
    }

    @Test
    void ownedPartsAreKeptAndCostNothing() {
        Gpu owned = Fixtures.one(Gpu.class);

        Recommendation recommendation = engine.recommend(catalog,
                new BuildRequest(new BigDecimal("30000"), Set.of(UseCase.GAMING_AAA), null, null, List.of(owned.id())));

        assertThat(recommendation.parts().gpu()).isEqualTo(owned);
        assertThat(recommendation.ownedIds()).containsExactly(owned.id());
        assertThat(recommendation.notes()).anyMatch(note -> note.contains("Aproveitamos"));
    }

    @Test
    void unknownOwnedPartIsRejected() {
        assertThatThrownBy(() -> engine.recommend(catalog, new BuildRequest(new BigDecimal("5000"), Set.of(UseCase.OFFICE_STUDY),
                null, null, List.of(java.util.UUID.randomUUID()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secondaryUsesWeighLessThanTheMainOne() {
        RequirementProfile gamingFirst = RequirementAnalyzer.analyze(new BuildRequest(new BigDecimal("5000"),
                Set.of(UseCase.GAMING_COMPETITIVE, UseCase.CONTAINERS_VMS), UseCase.GAMING_COMPETITIVE, null, List.of()));
        RequirementProfile containersFirst = RequirementAnalyzer.analyze(new BuildRequest(new BigDecimal("5000"),
                Set.of(UseCase.GAMING_COMPETITIVE, UseCase.CONTAINERS_VMS), UseCase.CONTAINERS_VMS, null, List.of()));

        assertThat(gamingFirst.gpuWeight()).isGreaterThan(containersFirst.gpuWeight());
        assertThat(containersFirst.cpuMultiThreadWeight()).isGreaterThan(gamingFirst.cpuMultiThreadWeight());
        assertThat(gamingFirst.minRamGb()).isEqualTo(32);
        assertThat(gamingFirst.gamingFocused()).isTrue();
        assertThat(containersFirst.gamingFocused()).isFalse();
    }

    @Test
    void higherResolutionRaisesVideoMemoryRequirement() {
        RequirementProfile qhd = RequirementAnalyzer.analyze(new BuildRequest(new BigDecimal("8000"),
                Set.of(UseCase.GAMING_AAA), null, TargetResolution.QHD, List.of()));

        assertThat(qhd.minVramGb()).isEqualTo(12);
    }

    @Test
    void alternativesAreOfferedOnlyForPartsToBuy() {
        Recommendation recommendation = engine.recommend(catalog,
                new BuildRequest(new BigDecimal("30000"), Set.of(UseCase.GAMING_AAA), null, null, List.of()));

        var alternatives = engine.alternatives(catalog, recommendation.parts(), Set.of(recommendation.parts().gpu().id()),
                recommendation.profile());

        assertThat(alternatives).doesNotContainKey(ComponentCategory.GPU);
    }
}
