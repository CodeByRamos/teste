package app.platform.config;

import app.platform.builds.BuildAssembler;
import app.platform.builds.SavedBuildRepository;
import app.platform.catalog.CatalogHolder;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.infra.opendb.OpenDbIngestion;
import app.platform.infra.persistence.JdbcCatalogRepository;
import app.platform.infra.persistence.JdbcSavedBuildRepository;
import app.platform.infra.pricing.ExamplePriceProvider;
import app.platform.pricing.PriceProvider;
import app.platform.pricing.PriceService;
import app.platform.recommendation.RecommendationEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Wires the framework-free domain (engines, catalog, pricing) to infrastructure. Domain classes carry no
 * Spring annotations; this is the only place that constructs them.
 */
@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformConfiguration {

    /** JSON for stored documents. Kept separate from the HTTP mapper so API tuning cannot break stored data. */
    private static final JsonMapper STORAGE_JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Bean
    CatalogHolder catalogHolder() {
        return new CatalogHolder();
    }

    @Bean
    CompatibilityEngine compatibilityEngine() {
        return CompatibilityEngine.withDefaultRules();
    }

    @Bean
    PriceService priceService(PlatformProperties properties) {
        List<PriceProvider> providers = new ArrayList<>();
        if (properties.pricing().examplePrices()) {
            providers.add(new ExamplePriceProvider());
        }
        return new PriceService(providers);
    }

    @Bean
    RecommendationEngine recommendationEngine(PriceService prices, CompatibilityEngine compatibility) {
        return new RecommendationEngine(prices, compatibility);
    }

    @Bean
    BuildAssembler buildAssembler(RecommendationEngine recommendations, CompatibilityEngine compatibility, PriceService prices) {
        return new BuildAssembler(recommendations, compatibility, prices);
    }

    @Bean
    JdbcCatalogRepository catalogRepository(JdbcTemplate jdbc) {
        return new JdbcCatalogRepository(jdbc, STORAGE_JSON);
    }

    @Bean
    SavedBuildRepository savedBuildRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        return new JdbcSavedBuildRepository(jdbc, transactions);
    }

    @Bean
    OpenDbIngestion openDbIngestion(JdbcTemplate jdbc, TransactionTemplate transactions) {
        return new OpenDbIngestion(jdbc, transactions, STORAGE_JSON);
    }

    static JsonMapper storageJson() {
        return STORAGE_JSON;
    }
}
