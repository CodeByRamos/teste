package app.platform.recommendation;

import app.platform.Fixtures;
import app.platform.catalog.Catalog;
import app.platform.catalog.CatalogVersion;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.ComponentInfo;
import app.platform.hardware.Cpu;
import app.platform.hardware.DataQuality;
import app.platform.hardware.Gpu;
import app.platform.hardware.HardwareComponent;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.SourceRef;
import app.platform.hardware.Storage;
import app.platform.infra.pricing.ExamplePriceProvider;
import app.platform.pricing.PriceService;
import app.platform.recommendation.UpgradeAdvice.Change;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An older PC (weak GPU, 8 GB, hard disk, small power supply) built around the real AM5 fixture CPU and board,
 * with the real fixture parts available as upgrade options.
 */
class UpgradeAdvisorTest {

    private final CompatibilityEngine compatibility = CompatibilityEngine.withDefaultRules();
    private final RecommendationEngine engine =
            new RecommendationEngine(new PriceService(List.of(new ExamplePriceProvider())), compatibility);
    private final UpgradeAdvisor advisor = new UpgradeAdvisor(engine, compatibility);

    private static ComponentInfo info(ComponentCategory category, String name) {
        SourceRef ref = new SourceRef("test", name);
        return new ComponentInfo(ref.internalId(), ref, category, name, null, null, null, null, new DataQuality(1, List.of()), null);
    }

    private final Gpu oldGpu = new Gpu(info(ComponentCategory.GPU, "Old GTX 1650"), "NVIDIA", "GeForce GTX 1650", 4, "GDDR5",
            896, 1665, 75, 200, 2.0, 3, 16, new Gpu.PowerConnectors(0, 0, 0));
    private final Memory oldMemory = new Memory(info(ComponentCategory.MEMORY, "Old 8GB"), "DDR5", "288-pin DIMM", 4800, 40, 1, 8, 8,
            false, false);
    private final Storage hardDisk = new Storage(info(ComponentCategory.STORAGE, "Old HDD"), "HDD", 1000, "3.5\"", "SATA 6.0 Gb/s", false);
    private final PowerSupply smallPsu = new PowerSupply(info(ComponentCategory.POWER_SUPPLY, "Old 450W"), 450, "ATX", "80+ Bronze",
            "Non-Modular", 140, 1, 0, 1);

    /** A second current-generation card, so the catalog has a performance range (one card alone has no scale). */
    private final Gpu entryGpu = new Gpu(info(ComponentCategory.GPU, "Entry RTX 3050"), "NVIDIA", "GeForce RTX 3050 8 GB", 8, "GDDR6",
            2560, 1777, 130, 200, 2.0, 4, 8, new Gpu.PowerConnectors(0, 1, 0));

    private Catalog catalogWith(HardwareComponent... extra) {
        List<HardwareComponent> all = new ArrayList<>(Fixtures.components());
        all.add(entryGpu);
        all.addAll(List.of(extra));
        return new Catalog(all, CatalogVersion.NONE);
    }

    private List<UUID> currentPc(PcCase pcCase) {
        return List.of(Fixtures.one(Cpu.class).id(), Fixtures.one(Motherboard.class).id(), oldGpu.id(), oldMemory.id(),
                hardDisk.id(), smallPsu.id(), pcCase.id(), Fixtures.one(app.platform.hardware.CpuCooler.class).id());
    }

    private static BuildRequest gaming(String budget) {
        return new BuildRequest(new BigDecimal(budget), Set.of(UseCase.GAMING_AAA), null, null, List.of());
    }

    @Test
    void findsTheBottlenecksOfTheCurrentPc() {
        Catalog catalog = catalogWith(oldGpu, oldMemory, hardDisk, smallPsu);

        UpgradeAdvice advice = advisor.advise(catalog, currentPc(Fixtures.one(PcCase.class)), gaming("6000"), null);

        assertThat(advice.assessment()).anySatisfy(a -> {
            assertThat(a.category()).isEqualTo(ComponentCategory.MEMORY);
            assertThat(a.level()).isEqualTo(UpgradeAdvice.Level.BOTTLENECK);
        });
        assertThat(advice.assessment()).anySatisfy(a -> {
            assertThat(a.category()).isEqualTo(ComponentCategory.STORAGE);
            assertThat(a.level()).isEqualTo(UpgradeAdvice.Level.BOTTLENECK);
        });
        assertThat(advice.assessment()).anySatisfy(a -> {
            assertThat(a.category()).isEqualTo(ComponentCategory.GPU);
            assertThat(a.level()).isIn(UpgradeAdvice.Level.WEAK, UpgradeAdvice.Level.BOTTLENECK);
        });
    }

    @Test
    void newGraphicsCardBringsTheRequiredPowerSupplyAlong() {
        Catalog catalog = catalogWith(oldGpu, oldMemory, hardDisk, smallPsu);

        UpgradeAdvice advice = advisor.advise(catalog, currentPc(Fixtures.one(PcCase.class)), gaming("6000"), ComponentCategory.GPU);

        UpgradeAdvice.UpgradePlan plan = advice.recommended();
        assertThat(plan).isNotNull();
        assertThat(plan.changes()).anySatisfy(change -> {
            assertThat(change.category()).isEqualTo(ComponentCategory.GPU);
            assertThat(change.role()).isEqualTo(Change.Role.MAIN);
        });
        assertThat(plan.changes()).anySatisfy(change -> {
            assertThat(change.category()).isEqualTo(ComponentCategory.POWER_SUPPLY);
            assertThat(change.role()).isEqualTo(Change.Role.REQUIRED);
            assertThat(change.replaces()).isEqualTo(smallPsu);
        });
        assertThat(plan.dependencies()).anyMatch(text -> text.contains("fonte"));
        assertThat(compatibility.check(plan.result()).isBuildable()).isTrue();
        assertThat(plan.result().cpu()).isEqualTo(Fixtures.one(Cpu.class));
    }

    @Test
    void tooShortCaseIsReplacedWhenTheNewCardDoesNotFit() {
        PcCase tiny = new PcCase(info(ComponentCategory.CASE, "Tiny case"), "Micro ATX Mini Tower", Set.of("Micro ATX", "Mini-ITX"),
                200, 170, null, Set.of(), false, 4, 20.0, false);
        Catalog catalog = catalogWith(oldGpu, oldMemory, hardDisk, smallPsu, tiny);

        UpgradeAdvice advice = advisor.advise(catalog, currentPc(tiny), gaming("8000"), ComponentCategory.GPU);

        assertThat(advice.recommended().changes()).anySatisfy(change -> {
            assertThat(change.category()).isEqualTo(ComponentCategory.CASE);
            assertThat(change.replaces()).isEqualTo(tiny);
        });
    }

    @Test
    void recommendationUsesTheBudgetOnTheBiggestCombinedGain() {
        Catalog catalog = catalogWith(oldGpu, oldMemory, hardDisk, smallPsu);

        UpgradeAdvice advice = advisor.advise(catalog, currentPc(Fixtures.one(PcCase.class)), gaming("30000"), null);

        UpgradeAdvice.UpgradePlan plan = advice.recommended();
        assertThat(plan.kind()).isEqualTo(UpgradeAdvice.UpgradePlan.Kind.COMBINED);
        assertThat(plan.changes()).extracting(Change::category)
                .contains(ComponentCategory.GPU, ComponentCategory.MEMORY, ComponentCategory.STORAGE);
        assertThat(plan.costBrl()).isLessThanOrEqualTo(new BigDecimal("30000"));
    }

    @Test
    void tinyBudgetStillFixesMemoryWhenThatIsWhatFits() {
        Catalog catalog = catalogWith(oldGpu, oldMemory, hardDisk, smallPsu);

        UpgradeAdvice advice = advisor.advise(catalog, currentPc(Fixtures.one(PcCase.class)), gaming("1000"), ComponentCategory.MEMORY);

        assertThat(advice.recommended().changes()).singleElement()
                .satisfies(change -> assertThat(change.category()).isEqualTo(ComponentCategory.MEMORY));
    }

    @Test
    void needsAtLeastProcessorAndMotherboard() {
        Catalog catalog = catalogWith(oldGpu);

        assertThatThrownBy(() -> advisor.advise(catalog, List.of(oldGpu.id()), gaming("3000"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processador e da placa-mãe");
    }
}
