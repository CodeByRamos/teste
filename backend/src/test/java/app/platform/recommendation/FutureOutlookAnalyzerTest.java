package app.platform.recommendation;

import app.platform.Fixtures;
import app.platform.catalog.Catalog;
import app.platform.catalog.CatalogVersion;
import app.platform.compatibility.BuildParts;
import app.platform.compatibility.CompatibilityEngine;
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
import app.platform.recommendation.FutureOutlook.Aspect;
import app.platform.recommendation.FutureOutlook.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FutureOutlookAnalyzerTest {

    private final FutureOutlookAnalyzer analyzer = new FutureOutlookAnalyzer(CompatibilityEngine.withDefaultRules());

    private static ComponentInfo info(ComponentCategory category, String name, Integer year) {
        SourceRef ref = new SourceRef("test", name);
        return new ComponentInfo(ref.internalId(), ref, category, name, null, null, null, year, new DataQuality(1, List.of()), null);
    }

    private final Gpu oldGpu = new Gpu(info(ComponentCategory.GPU, "Old GTX 1650", 2019), "NVIDIA", "GeForce GTX 1650", 4, "GDDR5",
            896, 1665, 75, 200, 2.0, 3, 16, new Gpu.PowerConnectors(0, 0, 0));
    private final Gpu entryGpu = new Gpu(info(ComponentCategory.GPU, "Entry RTX 3050", 2022), "NVIDIA", "GeForce RTX 3050 8 GB", 8, "GDDR6",
            2560, 1777, 130, 200, 2.0, 4, 8, new Gpu.PowerConnectors(0, 1, 0));
    private final Memory singleStick = new Memory(info(ComponentCategory.MEMORY, "Old 8GB", 2022), "DDR5", "288-pin DIMM", 4800, 40, 1, 8, 8,
            false, false);
    private final Storage hardDisk = new Storage(info(ComponentCategory.STORAGE, "Old HDD", 2018), "HDD", 1000, "3.5\"", "SATA 6.0 Gb/s", false);
    private final PowerSupply smallPsu = new PowerSupply(info(ComponentCategory.POWER_SUPPLY, "Old 550W", 2018), 550, "ATX", "80+ Bronze",
            "Non-Modular", 140, 1, 0, 1);

    private static Catalog catalogWith(HardwareComponent... extra) {
        List<HardwareComponent> all = new ArrayList<>(Fixtures.components());
        all.addAll(List.of(extra));
        return new Catalog(all, CatalogVersion.NONE);
    }

    private static Aspect aspect(FutureOutlook outlook, String id) {
        return outlook.aspects().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    /** AM5 fixture platform with a weak card, one memory stick, a hard disk and a small power supply. */
    private BuildParts olderAm5Pc() {
        return BuildParts.of(List.of(Fixtures.one(Cpu.class), Fixtures.one(Motherboard.class), oldGpu, singleStick, hardDisk,
                smallPsu, Fixtures.one(PcCase.class), Fixtures.one(CpuCooler.class)));
    }

    @Test
    void strongerCardThatTheCurrentPowerSupplyHandlesIsSuggestedAndThePowerSupplyIsNamedAsTheLimit() {
        Catalog catalog = catalogWith(oldGpu, entryGpu, singleStick, hardDisk, smallPsu);

        FutureOutlook outlook = analyzer.analyze(catalog, olderAm5Pc(), null);

        Aspect gpu = aspect(outlook, "gpu");
        assertThat(gpu.level()).isIn(Level.GOOD, Level.PARTIAL);
        assertThat(gpu.explanation()).contains(entryGpu.chipset()).contains("a fonte");
        // The RTX 4070 fixture needs more power than 550 W allows, so it is not offered as a drop-in.
        assertThat(gpu.explanation()).doesNotContain(Fixtures.one(Gpu.class).chipset());
    }

    @Test
    void freeSlotsAndCurrentMemoryGenerationAreReportedAsRoomToGrow() {
        Catalog catalog = catalogWith(oldGpu, entryGpu, singleStick, hardDisk, smallPsu);

        FutureOutlook outlook = analyzer.analyze(catalog, olderAm5Pc(), null);

        assertThat(aspect(outlook, "memory-room").level()).isEqualTo(Level.GOOD);
        assertThat(aspect(outlook, "memory-generation").level()).isEqualTo(Level.GOOD);
        assertThat(aspect(outlook, "storage").level()).isEqualTo(Level.GOOD);
        // The fixture CPU is the only AM5 processor in the catalog: nothing faster drops in.
        assertThat(aspect(outlook, "cpu").level()).isEqualTo(Level.LIMITED);
    }

    @Test
    void olderPlatformGetsADropInProcessorButItsMemoryGenerationIsFlagged() {
        Cpu am4Cpu = new Cpu(info(ComponentCategory.CPU, "AMD Ryzen 5 3600", 2019), "AM4", "Zen 2", 6, 6, 0, 12, 3.6, 4.2, null, 32.0,
                65, 88, false, "None", Set.of("DDR4"), 128, true);
        Cpu am4Upgrade = new Cpu(info(ComponentCategory.CPU, "AMD Ryzen 7 5800X3D", 2021), "AM4", "Zen 3", 8, 8, 0, 16, 3.4, 4.5, null, 96.0,
                105, 142, false, "None", Set.of("DDR4"), 128, false);
        Motherboard am4Board = new Motherboard(info(ComponentCategory.MOTHERBOARD, "Test B550 ATX", 2020), "AM4", "AMD B550", "ATX", "DDR4",
                4, 128, List.of(new Motherboard.M2Slot(Set.of("2280"), "M", "PCIe 4.0 x4")), 4,
                List.of(new Motherboard.PcieSlot("4.0", 1, 16)), null);
        Memory ddr4 = new Memory(info(ComponentCategory.MEMORY, "DDR4 16GB", 2020), "DDR4", "288-pin DIMM", 3200, 16, 2, 8, 16, false, false);
        Catalog catalog = catalogWith(am4Cpu, am4Upgrade, am4Board, ddr4);
        BuildParts parts = BuildParts.of(List.of(am4Cpu, am4Board, ddr4, Fixtures.one(Gpu.class), Fixtures.one(Storage.class),
                Fixtures.one(PowerSupply.class), Fixtures.one(PcCase.class), Fixtures.one(CpuCooler.class)));

        FutureOutlook outlook = analyzer.analyze(catalog, parts, null);

        Aspect cpu = aspect(outlook, "cpu");
        assertThat(cpu.level()).isNotEqualTo(Level.LIMITED);
        assertThat(cpu.explanation()).contains(am4Upgrade.name()).contains("AM4").contains("depois de 2021");
        // Newest processors in the catalog (the 2023 AM5 fixture) use DDR5 only.
        Aspect generation = aspect(outlook, "memory-generation");
        assertThat(generation.level()).isEqualTo(Level.LIMITED);
        assertThat(generation.explanation()).contains("DDR5");
        assertThat(outlook.summary()).contains("geração da memória");
    }

    @Test
    void fasterProcessorThatOverloadsThePowerSupplyIsNamedWithWhatItRequires() {
        Cpu hungry = new Cpu(info(ComponentCategory.CPU, "Test Ryzen 9 hungry", 2025), "AM5", "Zen 5", 16, 16, 0, 32, 4.3, 5.7, null, 128.0,
                170, 400, false, "None", Set.of("DDR5"), 192, false);
        Catalog catalog = catalogWith(oldGpu, singleStick, hardDisk, smallPsu, hungry);

        Aspect cpu = aspect(analyzer.analyze(catalog, olderAm5Pc(), null), "cpu");

        assertThat(cpu.level()).isEqualTo(Level.LIMITED);
        assertThat(cpu.headline()).contains("exigem trocar também a fonte");
        assertThat(cpu.explanation()).contains(hungry.name());
    }

    @Test
    void wifiCardSlotsDoNotCountAsDriveSlots() {
        Motherboard.M2Slot wifi = new Motherboard.M2Slot(Set.of("2230"), "E", "PCIe 4.0 x1");
        Motherboard.M2Slot drive = new Motherboard.M2Slot(Set.of("2280"), "M", "PCIE 3.0 x4");

        assertThat(wifi.acceptsNvme()).isFalse();
        assertThat(drive.acceptsNvme()).isTrue();
        Motherboard board = new Motherboard(info(ComponentCategory.MOTHERBOARD, "Board with Wi-Fi slot", 2023), "AM5", "AMD B650", "ATX",
                "DDR5", 4, 192, List.of(drive, wifi), 4, List.of(), null);
        assertThat(board.driveM2Slots()).containsExactly(drive);
    }

    @Test
    void buildWithoutMotherboardHasNoOutlook() {
        BuildParts parts = BuildParts.of(List.of(Fixtures.one(Cpu.class), Fixtures.one(Gpu.class)));

        assertThat(analyzer.analyze(catalogWith(), parts, null)).isNull();
    }

    @Test
    void gainTextRoundsLikeTheRestOfTheInterface() {
        assertThat(FutureOutlookAnalyzer.gainText(1.43)).isEqualTo("cerca de 45% a mais de desempenho");
        assertThat(FutureOutlookAnalyzer.gainText(2.1)).isEqualTo("cerca de 2× o desempenho");
        assertThat(FutureOutlookAnalyzer.gainText(2.7)).isEqualTo("cerca de 2,5× o desempenho");
    }
}
