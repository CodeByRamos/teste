package app.platform.compatibility;

import app.platform.Fixtures;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.ComponentInfo;
import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.DataQuality;
import app.platform.hardware.Gpu;
import app.platform.hardware.HardwareComponent;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.SourceRef;
import app.platform.hardware.Storage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityEngineTest {

    private final CompatibilityEngine engine = CompatibilityEngine.withDefaultRules();

    private static BuildParts realBuild() {
        return BuildParts.of(Fixtures.components());
    }

    private static CompatibilityFinding finding(CompatibilityReport report, String ruleId) {
        return report.findings().stream().filter(f -> f.ruleId().equals(ruleId)).findFirst().orElseThrow();
    }

    private static ComponentInfo info(ComponentCategory category, String name) {
        SourceRef ref = new SourceRef("test", name);
        return new ComponentInfo(ref.internalId(), ref, category, name, null, null, null, null, new DataQuality(1, List.of()));
    }

    @Test
    void realFixtureBuildIsCompatible() {
        CompatibilityReport report = engine.check(realBuild());

        assertThat(report.overall()).isEqualTo(CompatibilityStatus.OK);
        assertThat(report.summary()).isEqualTo("Tudo certo. As peças são compatíveis entre si.");
        assertThat(report.power().estimatedLoadWatts()).isEqualTo(162 + 200 + 50);
    }

    @Test
    void cpuOnAnotherSocketIsIncompatibleWithPlainExplanation() {
        Cpu am4 = new Cpu(info(ComponentCategory.CPU, "Old CPU"), "AM4", "Zen 3", 6, 0, 0, 12, 3.7, 4.6, null, 32.0,
                65, 88, false, null, Set.of("DDR4"), 128, true);

        CompatibilityReport report = engine.check(realBuild().withCpu(am4).withCooler(null));

        assertThat(report.overall()).isEqualTo(CompatibilityStatus.INCOMPATIBLE);
        assertThat(finding(report, "cpu-board.socket").explanation()).contains("encaixe diferente");
        assertThat(report.summary()).startsWith("Essa configuração não funciona porque");
    }

    @Test
    void sameSocketNameDifferentGenerationIsCaught() {
        Cpu coffeeLake = new Cpu(info(ComponentCategory.CPU, "i5-9600K"), "LGA 1151", "Coffee Lake", 6, 0, 0, 6, 3.7, 4.6,
                null, 9.0, 95, null, true, "UHD 630", Set.of("DDR4"), 128, false);
        Motherboard z170 = new Motherboard(info(ComponentCategory.MOTHERBOARD, "Z170 board"), "LGA 1151", "Intel Z170", "ATX",
                "DDR4", 4, 64, List.of(), 6, List.of(new Motherboard.PcieSlot("3.0", 1, 16)), null);

        CompatibilityReport report = engine.check(BuildParts.empty().withCpu(coffeeLake).withMotherboard(z170));

        assertThat(finding(report, "cpu-board.socket").status()).isEqualTo(CompatibilityStatus.OK);
        assertThat(finding(report, "cpu-board.generation").status()).isEqualTo(CompatibilityStatus.INCOMPATIBLE);
    }

    @Test
    void biosUpdateCaseIsAWarningNotAFailure() {
        Cpu zen3 = new Cpu(info(ComponentCategory.CPU, "Ryzen 5 5600"), "AM4", "Zen 3", 6, 0, 0, 12, 3.5, 4.4, null, 32.0,
                65, 88, false, null, Set.of("DDR4"), 128, true);
        Motherboard b450 = new Motherboard(info(ComponentCategory.MOTHERBOARD, "B450"), "AM4", "AMD B450", "ATX",
                "DDR4", 4, 64, List.of(), 6, List.of(new Motherboard.PcieSlot("3.0", 1, 16)), null);

        CompatibilityReport report = engine.check(BuildParts.empty().withCpu(zen3).withMotherboard(b450));

        assertThat(finding(report, "cpu-board.generation").status()).isEqualTo(CompatibilityStatus.WARNING);
        assertThat(finding(report, "cpu-board.generation").explanation()).contains("atualização de BIOS");
    }

    @Test
    void gpuTooLongForCaseIsIncompatible() {
        BuildParts parts = realBuild();
        PcCase small = parts.pcCase();
        PcCase tiny = new PcCase(small.info(), small.formFactor(), small.supportedMotherboardFormFactors(), 200,
                small.maxCoolerHeightMm(), null, Set.of(), false, 7, 30.0, false);

        CompatibilityReport report = engine.check(parts.withCase(tiny));

        CompatibilityFinding gpuFit = finding(report, "gpu.case");
        assertThat(gpuFit.status()).isEqualTo(CompatibilityStatus.INCOMPATIBLE);
        assertThat(gpuFit.explanation()).contains("242 mm").contains("200 mm");
    }

    @Test
    void missingClearanceDataIsFlaggedAsUnverifiedWarning() {
        BuildParts parts = realBuild();
        PcCase unknown = new PcCase(parts.pcCase().info(), "ATX Mid Tower", Set.of("ATX", "Micro ATX"), 315, null,
                null, Set.of(), false, 7, 40.0, false);

        CompatibilityFinding coolerFit = finding(engine.check(parts.withCase(unknown)), "cooler.case");

        assertThat(coolerFit.status()).isEqualTo(CompatibilityStatus.WARNING);
        assertThat(coolerFit.verified()).isFalse();
    }

    @Test
    void underpoweredSupplyIsIncompatibleAndTightSupplyIsAWarning() {
        BuildParts parts = realBuild();
        PowerSupply base = parts.powerSupply();
        PowerSupply weak = new PowerSupply(base.info(), 350, "ATX", "80+ Bronze", "Non-Modular", 140, 2, 0, 1);
        PowerSupply tight = new PowerSupply(base.info(), 450, "ATX", "80+ Bronze", "Non-Modular", 140, 2, 0, 1);

        assertThat(finding(engine.check(parts.withPowerSupply(weak)), "power.capacity").status()).isEqualTo(CompatibilityStatus.INCOMPATIBLE);
        assertThat(finding(engine.check(parts.withPowerSupply(tight)), "power.capacity").status()).isEqualTo(CompatibilityStatus.WARNING);
    }

    @Test
    void cpuWithoutBundledCoolerNeedsOne() {
        CompatibilityReport report = engine.check(realBuild().withCooler(null));

        assertThat(finding(report, "essential.cooling").status()).isEqualTo(CompatibilityStatus.INCOMPATIBLE);
    }

    @Test
    void wrongMemoryGenerationIsIncompatible() {
        BuildParts parts = realBuild();
        Memory ddr4 = new Memory(parts.memory().info(), "DDR4", "288-pin DIMM", 3200, 16, 2, 16, 32, false, false);

        assertThat(finding(engine.check(parts.withMemory(ddr4)), "memory.type").status()).isEqualTo(CompatibilityStatus.INCOMPATIBLE);
    }

    @Test
    void sataM2DriveNeedsASataCapableSlot() {
        BuildParts parts = realBuild();
        Storage sataM2 = new Storage(parts.storage().getFirst().info(), "SSD", 1000, "M.2-2280", "M.2 SATA", false);

        assertThat(finding(engine.check(parts.withStorage(List.of(sataM2))), "storage.m2").status())
                .isEqualTo(CompatibilityStatus.INCOMPATIBLE);
    }

    @Test
    void partialBuildOnlyWarnsAboutMissingParts() {
        List<HardwareComponent> onlyCpuAndBoard = List.of(Fixtures.one(Cpu.class), Fixtures.one(Motherboard.class));

        CompatibilityReport report = engine.check(BuildParts.of(onlyCpuAndBoard));

        assertThat(finding(report, "essential.missing").status()).isEqualTo(CompatibilityStatus.WARNING);
        assertThat(finding(report, "cpu-board.socket").status()).isEqualTo(CompatibilityStatus.OK);
    }

    @Test
    void airCoolerCompatibleWithSocketList() {
        assertThat(Fit.coolerSocket(Fixtures.one(CpuCooler.class), Fixtures.one(Cpu.class))).isEqualTo(Fit.Verdict.YES);
        assertThat(Fit.gpuLengthInCase(Fixtures.one(Gpu.class), Fixtures.one(PcCase.class))).isEqualTo(Fit.Verdict.YES);
    }
}
