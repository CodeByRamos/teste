package app.platform.infra.opendb;

import app.platform.Fixtures;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.DataIssue;
import app.platform.hardware.Gpu;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.SourceRef;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class OpenDbRecordMapperTest {

    private final OpenDbRecordMapper mapper = new OpenDbRecordMapper();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void mapsRealCpuRecordAndPreservesExternalIdentity() {
        Cpu cpu = Fixtures.one(Cpu.class);

        assertThat(cpu.name()).isEqualTo("AMD Ryzen 7 7800X3D");
        assertThat(cpu.info().source()).isEqualTo(new SourceRef("buildcores-opendb", "4dcfffec-423b-4052-8aca-db6c0f84bb43"));
        assertThat(cpu.id()).isEqualTo(cpu.info().source().internalId());
        assertThat(cpu.socket()).isEqualTo("AM5");
        assertThat(cpu.cores()).isEqualTo(8);
        assertThat(cpu.threads()).isEqualTo(16);
        assertThat(cpu.l3CacheMb()).isEqualTo(96.0);
        assertThat(cpu.powerBudgetWatts()).isEqualTo(162);
        assertThat(cpu.memoryTypes()).containsExactly("DDR5");
        assertThat(cpu.includesCooler()).isFalse();
        assertThat(cpu.info().quality().score()).isEqualTo(1.0);
    }

    @Test
    void mapsGpuPowerConnectorsAndPcieLink() {
        Gpu gpu = Fixtures.one(Gpu.class);

        assertThat(gpu.chipset()).isEqualTo("GeForce RTX 4070");
        assertThat(gpu.lengthMm()).isEqualTo(242);
        assertThat(gpu.powerConnectors()).isEqualTo(new Gpu.PowerConnectors(0, 1, 0));
        assertThat(gpu.pcieGeneration()).isEqualTo(4);
        assertThat(gpu.pcieLanes()).isEqualTo(16);
    }

    @Test
    void mapsMotherboardSlots() {
        Motherboard board = Fixtures.one(Motherboard.class);

        assertThat(board.m2Slots()).hasSize(2);
        assertThat(board.m2Slots().getFirst().sizes()).containsExactlyInAnyOrder("2280", "2260");
        assertThat(board.m2Slots().getFirst().acceptsNvme()).isTrue();
        assertThat(board.hasFullLengthPcieSlot()).isTrue();
        assertThat(board.sataPorts()).isEqualTo(4);
    }

    @Test
    void readsCaseClearances() {
        PcCase pcCase = Fixtures.one(PcCase.class);

        assertThat(pcCase.maxGpuLengthMm()).isEqualTo(315);
        assertThat(pcCase.maxCoolerHeightMm()).isEqualTo(170);
        assertThat(pcCase.includesPowerSupply()).isFalse();
    }

    @Test
    void discardsImplausibleValuesAndRecordsTheIssue() {
        JsonNode node = json.readTree("""
                {"opendb_id":"x-1","metadata":{"name":"Broken GPU","releaseYear":20117},
                 "chipset":"GeForce RTX 4070","memory":12,"core_count":5888,"core_boost_clock":2505,
                 "tdp":0,"length":2420,"power_connectors":{"pcie_8_pin":1}}
                """);

        Gpu gpu = (Gpu) mapper.map(ComponentCategory.GPU, node).orElseThrow().component();

        assertThat(gpu.tdpWatts()).isNull();
        assertThat(gpu.lengthMm()).isNull();
        assertThat(gpu.info().releaseYear()).isNull();
        assertThat(gpu.info().quality().issues()).extracting(DataIssue::field, DataIssue::kind)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("tdp", DataIssue.Kind.OUT_OF_RANGE),
                        org.assertj.core.groups.Tuple.tuple("length", DataIssue.Kind.OUT_OF_RANGE),
                        org.assertj.core.groups.Tuple.tuple("metadata.releaseYear", DataIssue.Kind.OUT_OF_RANGE));
        assertThat(gpu.info().quality().score()).isLessThan(1.0);
    }

    @Test
    void normalizesSocketAliasesAndDropsUnknownSockets() {
        JsonNode node = json.readTree("""
                {"opendb_id":"x-2","metadata":{"name":"Cooler"},"water_cooled":false,"height":150,
                 "cpu_sockets":["sTR4","LGA 115","AM5"]}
                """);

        CpuCooler cooler = (CpuCooler) mapper.map(ComponentCategory.CPU_COOLER, node).orElseThrow().component();

        assertThat(cooler.sockets()).containsExactlyInAnyOrder("TR4", "AM5");
        assertThat(cooler.info().quality().issues()).anyMatch(issue -> issue.kind() == DataIssue.Kind.UNRECOGNIZED);
    }

    @Test
    void rejectsRecordsWithoutIdentity() {
        assertThat(mapper.map(ComponentCategory.CPU, json.readTree("{\"metadata\":{\"name\":\"No id\"}}"))).isEmpty();
    }

    @Test
    void extractsUsableProductIdentifiersOnly() {
        JsonNode node = json.readTree("""
                {"opendb_id":"x-3","metadata":{"name":"SSD"},"identifiers":{"identifiers":[
                  {"type":"ean","value":"0824142335499"},{"type":"upc","value":"not-a-number"},
                  {"type":"mpn","value":"RTX 4070 VENTUS 2X WHITE 12G OCGeForce RTX 4070 VENTUS 2X WHITE 12G OCV513-403R"},
                  {"type":"mpn","value":"MZ-V9P2T0CW"}]}}
                """);

        var identifiers = mapper.map(ComponentCategory.STORAGE, node).orElseThrow().identifiers();

        assertThat(identifiers).extracting(OpenDbRecordMapper.ProductIdentifier::value)
                .containsExactlyInAnyOrder("0824142335499", "MZ-V9P2T0CW");
    }
}
