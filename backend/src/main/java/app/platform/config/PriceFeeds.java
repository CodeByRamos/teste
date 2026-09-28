package app.platform.config;

import app.platform.infra.pricing.feed.FeedFormat;
import app.platform.infra.pricing.feed.FeedImporter;
import app.platform.infra.pricing.feed.FeedStreams;
import app.platform.pricing.OfferValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Store price feeds: loads stored offers once the catalog is ready, then downloads each configured feed on a fixed
 * delay. One feed failing never affects the others or the rest of the application.
 */
@Component
public class PriceFeeds {

    private static final Logger log = LoggerFactory.getLogger(PriceFeeds.class);

    private final PlatformProperties properties;
    private final FeedImporter importer;
    private final Map<String, FeedImporter.Feed> feeds;
    private ScheduledExecutorService scheduler;

    PriceFeeds(PlatformProperties properties, FeedImporter importer) {
        this.properties = properties;
        this.importer = importer;
        this.feeds = properties.pricing().feeds().stream()
                .map(feed -> toFeed(feed, properties.pricing().maxOfferAge()))
                .collect(Collectors.toUnmodifiableMap(FeedImporter.Feed::id, Function.identity()));
    }

    /** Configured feed by id, for uploads through the admin endpoint. */
    public Optional<FeedImporter.Feed> feed(String id) {
        return Optional.ofNullable(feeds.get(id));
    }

    public FeedImporter importer() {
        return importer;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        importer.loadStored();
        List<PlatformProperties.Feed> downloadable = properties.pricing().feeds().stream().filter(feed -> feed.url() != null).toList();
        if (downloadable.isEmpty()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "price-feeds");
            thread.setDaemon(true);
            return thread;
        });
        long delay = properties.pricing().feedRefresh().toMinutes();
        scheduler.scheduleWithFixedDelay(() -> downloadable.forEach(this::refresh), 0, Math.max(delay, 15), TimeUnit.MINUTES);
    }

    private void refresh(PlatformProperties.Feed config) {
        try (InputStream input = FeedStreams.download(config.url())) {
            importer.importFeed(feeds.get(config.id()), input);
        } catch (Exception e) {
            // The message never contains the URL (it may hold an API key).
            log.warn("Price feed {} failed: {}", config.id(), e.getMessage());
        }
    }

    @jakarta.annotation.PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private static FeedImporter.Feed toFeed(PlatformProperties.Feed config, java.time.Duration maxAge) {
        if (config.id() == null || !config.id().matches("[a-z0-9-]{2,40}")) {
            throw new IllegalStateException("Price feed id must be lowercase letters, digits or dashes");
        }
        if (config.store() == null || config.store().isBlank()) {
            throw new IllegalStateException("Price feed " + config.id() + " needs a store name");
        }
        if (config.allowedDomains().isEmpty()) {
            throw new IllegalStateException("Price feed " + config.id() + " needs allowed-domains for its offer links");
        }
        Set<String> domains = new HashSet<>(config.allowedDomains());
        return new FeedImporter.Feed(config.id(), config.store().strip(), FeedFormat.of(config.format()), config.columns(),
                config.delimiter().charAt(0), new OfferValidation(domains, maxAge));
    }
}
