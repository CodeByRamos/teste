package app.platform.infra.pricing.crawl;

import app.platform.infra.pricing.feed.CatalogMatcher;
import app.platform.infra.pricing.feed.FeedStreams;
import app.platform.infra.pricing.feed.StoredOffer;
import app.platform.pricing.OfferValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The price bot for one store. Each {@link #step()} does exactly one network request (robots.txt, one sitemap or one
 * product page) and returns how long to wait before the next, so politeness is structural: one request at a time,
 * never faster than the store's Crawl-delay or our own minimum.
 */
public final class StoreCrawler {

    private static final Logger log = LoggerFactory.getLogger(StoreCrawler.class);

    static final Duration MATCHED_REVISIT = Duration.ofHours(6);
    static final Duration UNMATCHED_REVISIT = Duration.ofDays(7);
    static final Duration NOT_PRODUCT_REVISIT = Duration.ofDays(30);
    static final Duration BLOCKED_PAUSE = Duration.ofHours(1);
    static final Duration ROBOTS_BLOCKED_PAUSE = Duration.ofHours(24);
    static final Duration REFRESH_INTERVAL = Duration.ofHours(24);
    static final int MAX_SITEMAPS_PER_ROUND = 300;
    static final int MAX_URLS_PER_SITEMAP = 50_000;

    /**
     * @param id                  stable store id for offers (e.g. "terabyte-bot")
     * @param sitemaps            entry points (index or URL set)
     * @param sitemapFilter       child sitemaps to follow (e.g. hardware only); {@code null} follows all
     * @param productPattern      page URLs that are product pages
     * @param allowedDomains      hosts the bot may request (subdomains included)
     * @param minDelay            minimum time between requests
     */
    public record Config(String id, String storeName, List<URI> sitemaps, Pattern sitemapFilter, Pattern productPattern,
                         Set<String> allowedDomains, Duration minDelay, String productToken) {
    }

    /** What an operator sees for this store. */
    public record Status(String storeId, String storeName, Map<CrawlStore.Status, Integer> pages, int sitemapsRead,
                         int pagesDiscovered, int offersAccepted, int offersRejected, Instant pausedUntil, String lastProblem,
                         boolean robotsLoaded) {
    }

    private final Config config;
    private final PageFetcher fetcher;
    private final CrawlStore store;
    private final Supplier<CatalogMatcher> matcher;
    private final OfferValidation validation;
    private final Clock clock;
    private final Runnable onOffersChanged;

    private RobotsRules robots;
    private Instant robotsFetchedAt;
    private Instant sitemapsRefreshedAt;
    private final Deque<URI> sitemapQueue = new ArrayDeque<>();
    private int sitemapsThisRound;
    private Instant pausedUntil;
    private int consecutivePauses;
    private volatile String lastProblem;
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger sitemapsRead = new AtomicInteger();
    private final AtomicInteger discovered = new AtomicInteger();
    private final AtomicInteger rejected = new AtomicInteger();

    public StoreCrawler(Config config, PageFetcher fetcher, CrawlStore store, Supplier<CatalogMatcher> matcher,
                        OfferValidation validation, Clock clock, Runnable onOffersChanged) {
        this.config = config;
        this.fetcher = fetcher;
        this.store = store;
        this.matcher = matcher;
        this.validation = validation;
        this.clock = clock;
        this.onOffersChanged = onOffersChanged;
    }

    /** Does one unit of work; returns the wait before the next. Never throws. */
    public synchronized Duration step() {
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) {
            return Duration.between(now, pausedUntil);
        }
        try {
            if (robots == null || robotsFetchedAt.plus(REFRESH_INTERVAL).isBefore(now)) {
                refreshRobots(now);
                return politeDelay();
            }
            if (sitemapQueue.isEmpty() && (sitemapsRefreshedAt == null || sitemapsRefreshedAt.plus(REFRESH_INTERVAL).isBefore(now))) {
                sitemapQueue.addAll(config.sitemaps());
                sitemapsRefreshedAt = now;
                sitemapsThisRound = 0;
            }
            if (!sitemapQueue.isEmpty()) {
                readSitemap(sitemapQueue.poll());
                return politeDelay();
            }
            Optional<CrawlStore.Page> page = store.nextDue(config.id(), now);
            if (page.isEmpty()) {
                return Duration.ofMinutes(1);
            }
            visit(page.get(), now);
            return politeDelay();
        } catch (Exception e) {
            lastProblem = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Price bot {}: {}", config.id(), lastProblem);
            return politeDelay().multipliedBy(4);
        }
    }

    Duration politeDelay() {
        Duration delay = config.minDelay();
        if (robots != null && robots.crawlDelay().isPresent() && robots.crawlDelay().get().compareTo(delay) > 0) {
            delay = robots.crawlDelay().get();
        }
        return delay;
    }

    private void refreshRobots(Instant now) throws IOException {
        URI first = config.sitemaps().getFirst();
        URI robotsUri = URI.create("https://" + first.getHost() + "/robots.txt");
        PageFetcher.Response response = fetcher.get(robotsUri, null, null);
        robotsFetchedAt = now;
        switch (response.status()) {
            case 200 -> robots = RobotsRules.parse(new String(response.body(), StandardCharsets.UTF_8), config.productToken());
            case 404, 410 -> robots = RobotsRules.allowAll();
            case 401, 403 -> {
                // The store refuses robots outright: respect it, do not try to get around it.
                robots = RobotsRules.denyAll();
                pause(now, ROBOTS_BLOCKED_PAUSE, "robots.txt answered " + response.status() + " (store blocks robots)");
            }
            default -> {
                robots = RobotsRules.denyAll(); // unreachable robots.txt: assume nothing is allowed until it answers
                robotsFetchedAt = now.minus(REFRESH_INTERVAL).plus(Duration.ofMinutes(30));
                lastProblem = "robots.txt answered " + response.status();
            }
        }
    }

    private void readSitemap(URI uri) throws Exception {
        if (!allowedHost(uri)) {
            lastProblem = "sitemap skipped (host not allowed): " + uri;
            return;
        }
        if (!robots.allows(pathAndQuery(uri))) {
            lastProblem = "sitemap skipped (robots.txt): " + uri;
            return;
        }
        PageFetcher.Response response = fetcher.get(uri, null, null);
        if (blocking(response.status())) {
            pause(clock.instant(), BLOCKED_PAUSE, "sitemap answered " + response.status());
            sitemapQueue.addFirst(uri);
            return;
        }
        if (response.status() != 200) {
            lastProblem = "sitemap answered " + response.status() + ": " + uri;
            return;
        }
        sitemapsRead.incrementAndGet();
        SitemapParser.Sitemap sitemap = SitemapParser.parse(FeedStreams.decode(new ByteArrayInputStream(response.body())),
                MAX_URLS_PER_SITEMAP);
        for (String child : sitemap.childSitemaps()) {
            if (sitemapsThisRound < MAX_SITEMAPS_PER_ROUND
                    && (config.sitemapFilter() == null || config.sitemapFilter().matcher(child).find())) {
                sitemapQueue.add(URI.create(child));
                sitemapsThisRound++;
            }
        }
        List<String> products = sitemap.pages().stream()
                .filter(url -> config.productPattern().matcher(url).find())
                .filter(url -> {
                    URI page = URI.create(url);
                    return allowedHost(page) && robots.allows(pathAndQuery(page));
                })
                .toList();
        if (!products.isEmpty()) {
            store.discover(config.id(), products);
            discovered.addAndGet(products.size());
        }
    }

    private void visit(CrawlStore.Page page, Instant now) throws IOException {
        URI uri = URI.create(page.url());
        if (!allowedHost(uri) || !robots.allows(pathAndQuery(uri))) {
            store.save(with(page, CrawlStore.Status.DISALLOWED, null), now, now.plus(NOT_PRODUCT_REVISIT));
            return;
        }
        PageFetcher.Response response = fetcher.get(uri, page.etag(), page.lastModified());
        int status = response.status();
        if (response.notModified()) {
            store.save(page, now, now.plus(revisit(page.status())));
            return;
        }
        if (blocking(status)) {
            pause(now, BLOCKED_PAUSE, "page answered " + status);
            store.save(page, now, now.plus(BLOCKED_PAUSE));
            return;
        }
        if (status == 404 || status == 410 || (status >= 300 && status < 400)) {
            if (status >= 300 && response.location() != null) {
                URI target = uri.resolve(response.location());
                if (allowedHost(target) && config.productPattern().matcher(target.toString()).find()) {
                    store.discover(config.id(), List.of(target.toString()));
                }
            }
            dropOffer(page);
            store.save(with(page, CrawlStore.Status.NOT_PRODUCT, null), now, now.plus(NOT_PRODUCT_REVISIT));
            return;
        }
        if (status != 200) {
            int failures = page.failures() + 1;
            lastProblem = "page answered " + status;
            Duration backoff = Duration.ofHours(Math.min(1L << Math.min(failures, 7), 24 * 7));
            store.save(new CrawlStore.Page(page.storeId(), page.url(), CrawlStore.Status.ERROR, page.etag(),
                    page.lastModified(), page.componentId(), failures), now, now.plus(backoff));
            return;
        }
        consecutivePauses = 0;
        CrawlStore.Page fetched = new CrawlStore.Page(page.storeId(), page.url(), page.status(), response.etag(),
                response.lastModified(), page.componentId(), 0);
        Optional<ProductPageParser.PageProduct> product = ProductPageParser.parse(new String(response.body(), StandardCharsets.UTF_8));
        if (product.isEmpty()) {
            dropOffer(page);
            store.save(with(fetched, CrawlStore.Status.NOT_PRODUCT, null), now, now.plus(NOT_PRODUCT_REVISIT));
            return;
        }
        ProductPageParser.PageProduct listing = product.get();
        String listingTitle = listing.name();
        boolean brl = listing.currency() == null || listing.currency().equalsIgnoreCase("BRL");
        Optional<CatalogMatcher.Match> match = brl
                ? matcher.get().match(listing.gtin(), listing.mpn(), listing.brand(), listing.name())
                : Optional.empty();
        if (match.isEmpty()) {
            dropOffer(page);
            store.save(with(fetched, CrawlStore.Status.UNMATCHED, null), now, now.plus(UNMATCHED_REVISIT));
            store.annotate(config.id(), page.url(), listingTitle, null);
            return;
        }
        UUID componentId = match.get().componentId();
        if (page.componentId() != null && !page.componentId().equals(componentId)) {
            dropOffer(page); // the page now describes a different product
        }
        StoredOffer offer = new StoredOffer(componentId, config.id(), config.storeName(), listing.price(), page.url(),
                listing.availability(), now);
        Optional<OfferValidation.Rejection> rejection = validation.check(offer.priceBrl(), offer.url(), now, now,
                store.otherStorePrices(componentId, config.id(), now.minus(validation.maxAge())),
                store.currentPrice(componentId, config.id()).orElse(null));
        if (rejection.isPresent()) {
            rejected.incrementAndGet();
            lastProblem = "offer held back: " + rejection.get().name().toLowerCase(Locale.ROOT) + " (" + page.url() + ")";
        } else {
            store.upsertOffer(offer);
            store.observe(offer);
            accepted.incrementAndGet();
            onOffersChanged.run();
        }
        store.save(with(fetched, CrawlStore.Status.MATCHED, componentId), now, now.plus(MATCHED_REVISIT));
        store.annotate(config.id(), page.url(), listingTitle, match.get().method().name());
    }

    private void dropOffer(CrawlStore.Page page) {
        if (page.componentId() != null) {
            store.removeOffer(page.componentId(), config.id());
            onOffersChanged.run();
        }
    }

    private void pause(Instant now, Duration base, String reason) {
        consecutivePauses++;
        Duration pause = consecutivePauses >= 3 ? ROBOTS_BLOCKED_PAUSE : base; // keeps being refused: back off for a day
        pausedUntil = now.plus(pause);
        lastProblem = reason + "; paused until " + pausedUntil;
        log.warn("Price bot {} paused: {}", config.id(), lastProblem);
    }

    private static boolean blocking(int status) {
        return status == 401 || status == 403 || status == 429 || status == 503;
    }

    private static Duration revisit(CrawlStore.Status status) {
        return switch (status) {
            case MATCHED -> MATCHED_REVISIT;
            case UNMATCHED -> UNMATCHED_REVISIT;
            default -> NOT_PRODUCT_REVISIT;
        };
    }

    private static CrawlStore.Page with(CrawlStore.Page page, CrawlStore.Status status, UUID componentId) {
        return new CrawlStore.Page(page.storeId(), page.url(), status, page.etag(), page.lastModified(), componentId, 0);
    }

    private boolean allowedHost(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return "https".equalsIgnoreCase(uri.getScheme())
                && config.allowedDomains().stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    public Status status() {
        return new Status(config.id(), config.storeName(), store.counts(config.id()), sitemapsRead.get(), discovered.get(),
                accepted.get(), rejected.get(), pausedUntil, lastProblem, robots != null);
    }

    public String id() {
        return config.id();
    }
}
