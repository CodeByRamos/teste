package app.platform.config;

import app.platform.builds.BuildAssembler;
import app.platform.builds.SavedBuildRepository;
import app.platform.catalog.CatalogHolder;
import app.platform.compatibility.CompatibilityEngine;
import app.platform.intake.IntakeService;
import app.platform.intake.NeedsReader;
import app.platform.infra.ai.ClaudeNeedsReader;
import app.platform.infra.ai.RequestBudget;
import app.platform.infra.opendb.OpenDbIngestion;
import app.platform.infra.persistence.JdbcCatalogRepository;
import app.platform.infra.persistence.JdbcSavedBuildRepository;
import app.platform.infra.pricing.ExamplePriceProvider;
import app.platform.infra.pricing.JdbcPriceHistory;
import app.platform.infra.pricing.feed.FeedImporter;
import app.platform.infra.pricing.feed.StoreFeedPriceProvider;
import app.platform.infra.pricing.feed.StoreOfferRepository;
import app.platform.pricing.PriceProvider;
import app.platform.pricing.PriceHistory;
import app.platform.pricing.PriceService;
import app.platform.recommendation.BudgetExplorer;
import app.platform.recommendation.RecommendationEngine;
import app.platform.recommendation.UpgradeAdvisor;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PlatformConfiguration.class);

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
    StoreFeedPriceProvider storeFeedPriceProvider(PlatformProperties properties) {
        return new StoreFeedPriceProvider(properties.pricing().maxOfferAge(), java.time.Clock.systemUTC());
    }

    @Bean
    StoreOfferRepository storeOfferRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        return new StoreOfferRepository(jdbc, transactions, STORAGE_JSON);
    }

    @Bean
    FeedImporter feedImporter(StoreOfferRepository repository, StoreFeedPriceProvider provider, CatalogHolder catalogs) {
        return new FeedImporter(repository, provider, catalogs::current, java.time.Clock.systemUTC());
    }

    @Bean
    PriceService priceService(PlatformProperties properties, StoreFeedPriceProvider storeFeeds) {
        List<PriceProvider> providers = new ArrayList<>();
        // Real store offers first; example prices only fill in for components no store feed covers.
        providers.add(storeFeeds);
        if (properties.pricing().examplePrices()) {
            providers.add(new ExamplePriceProvider());
        }
        return new PriceService(providers);
    }

    @Bean
    RecommendationEngine recommendationEngine(PriceService prices, CompatibilityEngine compatibility) {
        return new RecommendationEngine(prices, compatibility);
    }

    /** Language model help for free-text requests: only with ANTHROPIC_API_KEY set; otherwise the rules alone. */
    @Bean
    NeedsReader needsReader(PlatformProperties properties) {
        PlatformProperties.Ai ai = properties.ai();
        if (!ai.enabled()) {
            log.info("AI interpretation off (no ANTHROPIC_API_KEY); free text is read by rules only");
            return NeedsReader.NONE;
        }
        log.info("AI interpretation on with model {}, at most {} calls per minute", ai.model(), ai.maxRequestsPerMinute());
        ClaudeNeedsReader reader = new ClaudeNeedsReader(
                AnthropicOkHttpClient.builder().apiKey(ai.apiKey()).timeout(ai.timeout()).maxRetries(1).build(),
                ai.model(), new RequestBudget(ai.maxRequestsPerMinute(), java.time.Clock.systemUTC()));
        Thread.ofVirtual().name("ai-warmup").start(reader::warmUp);
        return reader;
    }

    @Bean
    IntakeService intakeService(NeedsReader reader) {
        return new IntakeService(reader);
    }

    @Bean
    BudgetExplorer budgetExplorer(RecommendationEngine recommendations) {
        return new BudgetExplorer(recommendations);
    }

    @Bean
    UpgradeAdvisor upgradeAdvisor(RecommendationEngine recommendations, CompatibilityEngine compatibility) {
        return new UpgradeAdvisor(recommendations, compatibility);
    }

    @Bean
    PriceHistory priceHistory(JdbcTemplate jdbc) {
        return new JdbcPriceHistory(jdbc);
    }

    @Bean
    BuildAssembler buildAssembler(RecommendationEngine recommendations, CompatibilityEngine compatibility, PriceService prices,
                                  PriceHistory history) {
        return new BuildAssembler(recommendations, compatibility, prices, history, java.time.Clock.systemUTC());
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
