package app.platform;

import app.platform.hardware.Gpu;
import app.platform.hardware.HardwareComponent;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A store feed uploaded by an operator becomes real prices in builds; bad rows never reach anyone. */
@SpringBootTest
@AutoConfigureMockMvc
class PriceFeedIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = start();
    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    MockMvc mvc;

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
        registry.add("platform.admin.token", () -> ADMIN_TOKEN);
        registry.add("platform.pricing.feeds[0].id", () -> "loja-teste");
        registry.add("platform.pricing.feeds[0].store", () -> "Loja Teste");
        registry.add("platform.pricing.feeds[0].format", () -> "generic");
        registry.add("platform.pricing.feeds[0].allowed-domains", () -> "loja.example");
    }

    @AfterAll
    static void stop() throws IOException {
        POSTGRES.close();
    }

    @Test
    void uploadedFeedBecomesRealPricesAndRejectsWhatLooksWrong() throws Exception {
        String observed = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        String feed = """
                gtin,mpn,brand,price_brl,url,availability,observed_at
                0824142335499,,,"3.899,90",https://www.loja.example/rtx-4070,in stock,%1$s
                0765441867123,,,899.90,https://evil.example/ram,1,%1$s
                4006381333931,,,59.90,https://www.loja.example/other-product,1,%1$s
                ,100-100000910WOF,AMD,"2.199,00",https://www.loja.example/7800x3d,1,%1$s
                ,100-100000910WOF,Intel,1999.00,https://www.loja.example/wrong-brand,1,%1$s
                """.formatted(observed);

        mvc.perform(post("/admin/price-feeds/loja-teste").content(feed)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/price-feeds/loja-teste").header("X-Admin-Token", "wrong").content(feed)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/price-feeds/unknown").header("X-Admin-Token", ADMIN_TOKEN).content(feed)).andExpect(status().isNotFound());

        mvc.perform(post("/admin/price-feeds/loja-teste").header("X-Admin-Token", ADMIN_TOKEN).content(feed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsRead").value(5))
                // GPU by GTIN, RAM by GTIN (then rejected), CPU by MPN + brand; the "Intel" row does not match.
                .andExpect(jsonPath("$.rowsMatched").value(3))
                .andExpect(jsonPath("$.matchedByMpn").value(1))
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.rejections.DOMAIN_NOT_ALLOWED").value(1))
                .andExpect(jsonPath("$.applied").value(true));

        String ids = Fixtures.components().stream().map(HardwareComponent::id).map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));
        String body = mvc.perform(post("/api/builds/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentIds\": [" + ids + "], \"ownedComponentIds\": []}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode items = json.readTree(body).get("items");
        String gpuId = Fixtures.one(Gpu.class).id().toString();
        for (JsonNode item : items) {
            JsonNode price = item.get("price");
            if (item.get("component").get("id").stringValue().equals(gpuId)) {
                assertThat(price.get("kind").stringValue()).isEqualTo("REAL");
                assertThat(price.get("amountBrl").decimalValue()).isEqualByComparingTo("3899.90");
                assertThat(price.get("storeName").stringValue()).isEqualTo("Loja Teste");
                assertThat(price.get("url").stringValue()).isEqualTo("https://www.loja.example/rtx-4070");
            } else if (item.get("category").stringValue().equals("CPU")) {
                assertThat(price.get("kind").stringValue()).isEqualTo("REAL");
                assertThat(price.get("amountBrl").decimalValue()).isEqualByComparingTo("2199.00");
            } else if (item.get("category").stringValue().equals("MEMORY")) {
                // The RAM row pointed to a host outside the store's domains: it stays on the labeled example price.
                assertThat(price.get("kind").stringValue()).isEqualTo("EXAMPLE");
            }
        }
    }
}
