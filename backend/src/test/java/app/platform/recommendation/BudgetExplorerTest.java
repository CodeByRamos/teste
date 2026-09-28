package app.platform.recommendation;

import app.platform.Fixtures;
import app.platform.compatibility.BuildParts;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.ComponentInfo;
import app.platform.hardware.DataQuality;
import app.platform.hardware.Gpu;
import app.platform.hardware.SourceRef;
import app.platform.infra.pricing.ExamplePriceProvider;
import app.platform.pricing.PriceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetExplorerTest {

    private final BudgetExplorer explorer = new BudgetExplorer(new RecommendationEngine(
            new PriceService(List.of(new ExamplePriceProvider())), CompatibilityEngine.withDefaultRules()));

    private static BuildRequest gaming(String budget) {
        return new BuildRequest(new BigDecimal(budget), Set.of(UseCase.GAMING_AAA), null, null, List.of());
    }

    @Test
    void stepsAreAboutAFifthOfTheBudgetInRoundAmounts() {
        assertThat(BudgetExplorer.step(new BigDecimal("5000"))).isEqualByComparingTo("1000");
        assertThat(BudgetExplorer.step(new BigDecimal("8000"))).isEqualByComparingTo("1500");
        assertThat(BudgetExplorer.step(new BigDecimal("1500"))).isEqualByComparingTo("500");
        assertThat(BudgetExplorer.step(new BigDecimal("60000"))).isEqualByComparingTo("5000");
    }

    @Test
    void whenMoreMoneyBuysTheSamePartsItSaysSo() {
        // The fixture catalog has one part per category: a bigger budget cannot buy anything better.
        List<BudgetExplorer.BudgetOption> options = explorer.explore(Fixtures.catalog(), gaming("30000"));

        assertThat(options).hasSize(2);
        BudgetExplorer.BudgetOption higher = options.get(1);
        assertThat(higher.budgetBrl()).isEqualByComparingTo("35000");
        assertThat(higher.feasible()).isTrue();
        assertThat(higher.notWorthIt()).isTrue();
        assertThat(higher.changes()).isEmpty();
        assertThat(higher.performanceText()).isEqualTo("praticamente o mesmo desempenho em jogos");
    }

    @Test
    void aBudgetThatCannotBuyACompletePcIsReportedAsSuch() {
        List<BudgetExplorer.BudgetOption> options = explorer.explore(Fixtures.catalog(), gaming("2000"));

        BudgetExplorer.BudgetOption lower = options.getFirst();
        assertThat(lower.budgetBrl()).isEqualByComparingTo("1500");
        assertThat(lower.feasible()).isFalse();
        assertThat(lower.totalBrl()).isNull();
    }

    @Test
    void graphicsCardChangeIsListedWithBothNames() {
        Gpu fixture = Fixtures.one(Gpu.class);
        SourceRef ref = new SourceRef("test", "Stronger card");
        Gpu stronger = new Gpu(new ComponentInfo(ref.internalId(), ref, ComponentCategory.GPU, "Stronger card", null, null, null, 2025,
                new DataQuality(1, List.of()), null), fixture.chipsetManufacturer(), "GeForce RTX 5080", 16, "GDDR7",
                10752, 2617, 360, fixture.lengthMm(), fixture.slotWidth(), fixture.pcieGeneration(), fixture.pcieLanes(),
                fixture.powerConnectors());
        BuildParts current = BuildParts.of(Fixtures.components());

        List<BudgetExplorer.PartChange> changes = BudgetExplorer.changes(current, current.withGpu(stronger));

        assertThat(changes).containsExactly(new BudgetExplorer.PartChange(ComponentCategory.GPU, fixture.name(), "Stronger card"));
        assertThat(BudgetExplorer.performanceRatio(RequirementAnalyzer.analyze(gaming("8000")), current, current.withGpu(stronger)))
                .isGreaterThan(1.5);
    }
}
