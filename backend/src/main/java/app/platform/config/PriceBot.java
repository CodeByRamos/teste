package app.platform.config;

import app.platform.catalog.CatalogHolder;
import app.platform.infra.pricing.crawl.CrawlStore;
import app.platform.infra.pricing.crawl.HttpPageFetcher;
import app.platform.infra.pricing.crawl.JdbcCrawlStore;
import app.platform.infra.pricing.crawl.StoreCrawler;
import app.platform.infra.pricing.feed.CatalogMatcher;
import app.platform.infra.pricing.feed.StoreFeedPriceProvider;
import app.platform.infra.pricing.feed.StoreOfferRepository;
import app.platform.pricing.OfferValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Runs one {@link StoreCrawler} per enabled store, each on its own thread, one request at a time. Offer changes are
 * pushed to the price provider at most once a minute; the catalog index used for matching is refreshed hourly.
 */
@Component
public class PriceBot {

    private static final Logger log = LoggerFactory.getLogger(PriceBot.class);
    private static final Duration MATCHER_REFRESH = Duration.ofHours(1);

    private final PlatformProperties properties;
    private final StoreOfferRepository offers;
    private final StoreFeedPriceProvider provider;
    private final CatalogHolder catalogs;
    private final CrawlStore store;
    private final List<StoreCrawler> crawlers = new ArrayList<>();
    private final AtomicBoolean offersChanged = new AtomicBoolean();
    private volatile boolean running;
    private volatile CatalogMatcher matcher;
    private volatile Instant matcherBuiltAt = Instant.EPOCH;

    PriceBot(PlatformProperties properties, StoreOfferRepository offers, StoreFeedPriceProvider provider,
             CatalogHolder catalogs, JdbcTemplate jdbc) {
        this.properties = properties;
        this.offers = offers;
        this.provider = provider;
        this.catalogs = catalogs;
        this.store = new JdbcCrawlStore(jdbc);
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        PlatformProperties.Pricing pricing = properties.pricing();
        List<PlatformProperties.Crawler> enabled = pricing.crawlers().stream().filter(PlatformProperties.Crawler::enabled).toList();
        if (enabled.isEmpty()) {
            return;
        }
        if (pricing.botContact() == null) {
            log.warn("Price bot not started: set PRICE_BOT_CONTACT (an e-mail or URL) so stores can identify and reach it");
            return;
        }
        String userAgent = pricing.botName() + "/1.0 (+" + pricing.botContact() + ")";
        HttpPageFetcher fetcher = new HttpPageFetcher(userAgent);
        for (PlatformProperties.Crawler config : enabled) {
            crawlers.add(new StoreCrawler(toConfig(config, pricing.botName()), fetcher, store, this::matcher,
                    new OfferValidation(new HashSet<>(config.allowedDomains()), pricing.maxOfferAge()), Clock.systemUTC(),
                    () -> offersChanged.set(true)));
        }
        running = true;
        for (StoreCrawler crawler : crawlers) {
            Thread.ofVirtual().name("price-bot-" + crawler.id()).start(() -> loop(crawler));
        }
        Thread.ofVirtual().name("price-bot-publisher").start(this::publishLoop);
        log.info("Price bot started for {} as \"{}\"", crawlers.stream().map(StoreCrawler::id).toList(), userAgent);
    }

    private void loop(StoreCrawler crawler) {
        while (running) {
            Duration wait = crawler.step();
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Pushes offer changes to the site at most once a minute (reloading all offers is not free). */
    private void publishLoop() {
        while (running) {
            try {
                Thread.sleep(Duration.ofMinutes(1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (offersChanged.getAndSet(false)) {
                provider.replaceAll(offers.all());
            }
        }
    }

    private CatalogMatcher matcher() {
        Instant now = Instant.now();
        if (matcher == null || matcherBuiltAt.plus(MATCHER_REFRESH).isBefore(now)) {
            matcher = CatalogMatcher.load(offers, catalogs.current());
            matcherBuiltAt = now;
        }
        return matcher;
    }

    public List<StoreCrawler.Status> status() {
        return crawlers.stream().map(StoreCrawler::status).toList();
    }

    @jakarta.annotation.PreDestroy
    void stop() {
        running = false;
    }

    private static StoreCrawler.Config toConfig(PlatformProperties.Crawler config, String productToken) {
        if (config.id() == null || !config.id().matches("[a-z0-9-]{2,40}")) {
            throw new IllegalStateException("Price bot store id must be lowercase letters, digits or dashes");
        }
        if (config.sitemaps().isEmpty() || config.allowedDomains().isEmpty() || config.productPattern() == null) {
            throw new IllegalStateException("Price bot store " + config.id() + " needs sitemaps, allowed-domains and product-pattern");
        }
        return new StoreCrawler.Config(config.id(), config.store() == null ? config.id() : config.store(), config.sitemaps(),
                config.sitemapFilter() == null || config.sitemapFilter().isBlank() ? null : Pattern.compile(config.sitemapFilter()),
                Pattern.compile(config.productPattern()), new HashSet<>(config.allowedDomains()), config.minDelay(), productToken);
    }
}
