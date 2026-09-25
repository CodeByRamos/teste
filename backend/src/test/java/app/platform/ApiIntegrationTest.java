package app.platform;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack against a real PostgreSQL: migrations, OpenDB ingestion, catalog load and the public API. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = start();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("platform.opendb.snapshot-dir", () -> Fixtures.directory().toString());
        registry.add("platform.opendb.ingest-on-startup", () -> "true");
        registry.add("platform.pricing.example-prices", () -> "true");
    }

    @AfterAll
    static void stop() throws IOException {
        POSTGRES.close();
    }

    @Test
    void ingestsSnapshotKeepingRawRecordsAndIdentifiers() {
        assertThat(jdbc.queryForObject("select count(*) from hardware_component where status = 'active'", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "select raw->'metadata'->>'name' from hardware_component where external_id = '4dcfffec-423b-4052-8aca-db6c0f84bb43'",
                String.class)).isEqualTo("AMD Ryzen 7 7800X3D");
        assertThat(jdbc.queryForObject("select count(*) from component_identifier", Integer.class)).isPositive();
    }

    @Test
    void recommendsSavesAndReturnsTheSameBuild() throws Exception {
        String needs = """
                {"budgetBrl": 30000, "useCases": ["GAMING_AAA"]}
                """;
        MvcResult recommended = mvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON).content(needs))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(8))
                .andExpect(jsonPath("$.compatibility.overall").value("OK"))
                .andExpect(jsonPath("$.totals.pricesAreExamples").value(true))
                .andExpect(jsonPath("$.items[0].component.source.name").value("BuildCores OpenDB"))
                .andReturn();

        JsonNode build = json.readTree(recommended.getResponse().getContentAsString());
        StringBuilder ids = new StringBuilder();
        for (JsonNode item : build.get("items")) {
            ids.append(ids.isEmpty() ? "" : ",").append('"').append(item.get("component").get("id").stringValue()).append('"');
        }
        String save = """
                {"needs": %s, "componentIds": [%s], "ownedComponentIds": [], "title": "Teste"}
                """.formatted(needs, ids);
        MvcResult saved = mvc.perform(post("/api/builds").contentType(MediaType.APPLICATION_JSON).content(save))
                .andExpect(status().isCreated())
                .andReturn();
        String id = json.readTree(saved.getResponse().getContentAsString()).get("id").stringValue();

        mvc.perform(get("/api/builds/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Teste"))
                .andExpect(jsonPath("$.build.items.length()").value(8))
                .andExpect(jsonPath("$.build.totals.totalBrl").value(build.get("totals").get("totalBrl").decimalValue().doubleValue()));
    }

    @Test
    void rejectsInvalidInputWithPlainMessage() throws Exception {
        mvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetBrl\": 10, \"useCases\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"));
    }

    @Test
    void unknownSavedBuildIs404() throws Exception {
        mvc.perform(get("/api/builds/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void exposesAttributionAndDataQuality() throws Exception {
        mvc.perform(get("/api/meta/data-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hardware.license").value("Open Data Commons Attribution License (ODC-By) v1.0"))
                .andExpect(jsonPath("$.quality.CPU.records").value(1));
    }

    @Test
    void searchFindsComponentsAccentInsensitively() throws Exception {
        mvc.perform(get("/api/catalog/search").param("category", "CPU").param("q", "ryzen 7800"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("AMD Ryzen 7 7800X3D"));
    }
}
