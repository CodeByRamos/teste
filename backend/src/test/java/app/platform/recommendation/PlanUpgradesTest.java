package app.platform.recommendation;

import app.platform.Fixtures;
import app.platform.catalog.Catalog;
import app.platform.catalog.CatalogVersion;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.compatibility.PowerEstimator;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.ComponentInfo;
import app.platform.hardware.Cpu;
import app.platform.hardware.DataQuality;
import app.platform.hardware.HardwareComponent;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.SourceRef;
import app.platform.infra.pricing.ExamplePriceProvider;
import app.platform.intake.NeedsInterpreter;
import app.platform.pricing.PriceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "I want to upgrade later": the fixture AM5/DDR5 platform competes with an older, cheaper AM4/DDR4 one,
 * and a just-sufficient power supply competes with one that has headroom.
 */
class PlanUpgradesTest {

    private final RecommendationEngine engine = new RecommendationEngine(
            new PriceService(List.of(new ExamplePriceProvider())), CompatibilityEngine.withDefaultRules());

    private static ComponentInfo info(ComponentCategory category, String name, Integer year) {
        SourceRef ref = new SourceRef("test", name);
        return new ComponentInfo(ref.internalId(), ref, category, name, null, null, null, year, new DataQuality(1, List.of()), null);
    }

    private final Cpu am4Cpu = new Cpu(info(ComponentCategory.CPU, "AMD Ryzen 7 5700X3D", 2022), "AM4", "Zen 3", 8, 8, 0, 16, 3.0, 4.1, null,
            96.0, 105, 142, false, "None", Set.of("DDR4"), 128, false);
    private final Motherboard am4Board = new Motherboard(info(ComponentCategory.MOTHERBOARD, "Test B550 ATX", 2020), "AM4", "AMD B550",
            "ATX", "DDR4", 4, 128, List.of(new Motherboard.M2Slot(Set.of("2280"), "M", "PCIe 4.0 x4")), 4,
            List.of(new Motherboard.PcieSlot("4.0", 1, 16)), null);
    private final Memory ddr4 = new Memory(info(ComponentCategory.MEMORY, "DDR4-3200 32GB (2x16GB)", 2020), "DDR4", "288-pin DIMM",
            3200, 16, 2, 16, 32, false, false);
    private final PowerSupply justEnough = new PowerSupply(info(ComponentCategory.POWER_SUPPLY, "Test 650W Bronze", 2022), 650, "ATX",
            "80+ Bronze", "Non-Modular", 140, 2, 1, 1);

    private Catalog catalog() {
        List<HardwareComponent> all = new ArrayList<>(Fixtures.components());
        all.addAll(List.of(am4Cpu, am4Board, ddr4, justEnough));
        return new Catalog(all, CatalogVersion.NONE);
    }

    private static BuildRequest gaming(boolean planUpgrades) {
        return new BuildRequest(new BigDecimal("30000"), Set.of(UseCase.GAMING_AAA), null, null, List.of(), planUpgrades);
    }

    @Test
    void planningUpgradesPicksACurrentPlatformCurrentMemoryAndPowerHeadroom() {
        Recommendation recommendation = engine.recommend(catalog(), gaming(true));

        // The AM5 fixture CPU (2023) is the newest in this catalog, so AM5 is the platform still receiving processors.
        assertThat(recommendation.parts().cpu().socket()).isEqualTo("AM5");
        assertThat(recommendation.parts().memory().ramType()).isEqualTo("DDR5");
        int recommended = PowerEstimator.estimate(recommendation.parts()).recommendedPsuWatts();
        assertThat(recommendation.parts().powerSupply().wattage())
                .isGreaterThanOrEqualTo(recommended + RecommendationEngine.UPGRADE_PSU_HEADROOM_WATTS);
        assertThat(recommendation.profile().reasons()).anyMatch(reason -> reason.contains("melhorar o PC aos poucos"));
        // The cost of the preference is stated either way.
        assertThat(recommendation.notes()).anyMatch(note -> note.contains("upgrades"));
    }

    @Test
    void withoutThePreferenceTheCheaperSufficientPowerSupplyIsEnough() {
        Recommendation recommendation = engine.recommend(catalog(), gaming(false));

        int recommended = PowerEstimator.estimate(recommendation.parts()).recommendedPsuWatts();
        assertThat(recommendation.parts().powerSupply().wattage()).isGreaterThanOrEqualTo(recommended);
        assertThat(recommendation.profile().planUpgrades()).isFalse();
    }

    @Test
    void freeTextAboutUpgradingLaterTurnsThePreferenceOn() {
        assertThat(NeedsInterpreter.interpret("PC de 6 mil para jogar, quero ir melhorando aos poucos").planUpgrades()).isTrue();
        assertThat(NeedsInterpreter.interpret("Quero poder fazer upgrade depois").planUpgrades()).isTrue();
        assertThat(NeedsInterpreter.interpret("PC de 6 mil para jogar").planUpgrades()).isFalse();
    }
}
