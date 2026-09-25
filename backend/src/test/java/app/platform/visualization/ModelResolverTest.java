package app.platform.visualization;

import app.platform.Fixtures;
import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.Gpu;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.Storage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real OpenDB records → model family, variant and parameters. */
class ModelResolverTest {

    private static ModelSpec only(List<ModelSpec> specs) {
        assertThat(specs).hasSize(1);
        return specs.getFirst();
    }

    @Test
    void dualFanCardUsesItsRealLengthSlotsAndColour() {
        ModelSpec spec = only(ModelResolver.resolve(Fixtures.one(Gpu.class)));

        assertThat(spec.modelId()).isEqualTo("gpu-dual-fan-v1");
        assertThat(spec.params()).containsEntry("lengthMm", 242).containsEntry("fanCount", 2).containsEntry("slots", 2.0);
        assertThat(spec.color()).isEqualTo("WHITE");
        assertThat(spec.confidence()).isEqualTo("measured");
        assertThat(spec.assumptions()).anyMatch(text -> text.contains("altura"));
    }

    @Test
    void caseKeepsItsMeasuredDimensions() {
        ModelSpec spec = only(ModelResolver.resolve(Fixtures.one(PcCase.class)));

        assertThat(spec.modelId()).isEqualTo("case-atx-mid-tower-v1");
        assertThat(spec.params()).containsEntry("widthMm", 217.0).containsEntry("heightMm", 453.0).containsEntry("depthMm", 413.0);
        assertThat(spec.confidence()).isEqualTo("measured");
    }

    @Test
    void dualTowerCoolerIsRecognisedWithItsFans() {
        ModelSpec spec = only(ModelResolver.resolve(Fixtures.one(CpuCooler.class)));

        assertThat(spec.modelId()).isEqualTo("cooler-dual-tower-v1");
        assertThat(spec.params()).containsEntry("heightMm", 158).containsEntry("fanSizeMm", 140).containsEntry("fanCount", 2);
    }

    @Test
    void otherCategoriesMapToTheirFamilies() {
        assertThat(only(ModelResolver.resolve(Fixtures.one(Motherboard.class))).modelId()).isEqualTo("mb-matx-v1");
        assertThat(only(ModelResolver.resolve(Fixtures.one(Memory.class))).modelId()).isEqualTo("ram-heatsink-v1");
        assertThat(only(ModelResolver.resolve(Fixtures.one(PowerSupply.class))).modelId()).isEqualTo("psu-atx-v1");
        ModelSpec ssd = only(ModelResolver.resolve(Fixtures.one(Storage.class)));
        assertThat(ssd.modelId()).isEqualTo("ssd-m2-v1");
        assertThat(ssd.params()).containsEntry("lengthMm", 80);
        assertThat(ModelResolver.resolve(Fixtures.one(Cpu.class))).isEmpty();
    }

    @Test
    void everyResolvableModelExistsInTheGeneratedCatalog() throws IOException {
        Path catalog = Path.of("..", "frontend", "public", "3d-models", "catalog.json");
        JsonNode root = JsonMapper.builder().build().readTree(Files.readString(catalog));
        Map<String, JsonNode> models = new HashMap<>();
        for (JsonNode model : root.path("models")) {
            models.put(model.path("modelId").stringValue(), model);
        }
        for (String modelId : ModelResolver.MODEL_IDS) {
            assertThat(models).as("catalog entry for %s", modelId).containsKey(modelId);
            assertThat(models.get(modelId).path("status").stringValue()).as("%s implemented", modelId).isEqualTo("implemented");
            assertThat(models.get(modelId).path("url").isString()).as("%s has a GLB", modelId).isTrue();
        }
    }
}
