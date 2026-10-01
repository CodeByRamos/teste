package app.platform.infra.pricing.crawl;

import app.platform.Fixtures;
import app.platform.hardware.Gpu;
import app.platform.infra.pricing.feed.CatalogMatcher;
import app.platform.infra.pricing.feed.StoredOffer;
import app.platform.pricing.Gtin;
import app.platform.pricing.OfferValidation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole bot for one store against a fake website: discovery, politeness, matching, blocking. */
class StoreCrawlerTest {

    private static final String HOST = "https://www.loja.example";
    private final Gpu gpu = Fixtures.one(Gpu.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-30T12:00:00Z"));
    private final FakeSite site = new FakeSite();
    private final MemoryStore store = new MemoryStore();

    private StoreCrawler crawler() {
        CatalogMatcher matcher = new CatalogMatcher(
                Map.of(Gtin.normalize("0824142335499").orElseThrow(), Set.of(gpu.id())), Map.of(), Fixtures.catalog());
        return new StoreCrawler(new StoreCrawler.Config("loja-bot", "Loja Teste", List.of(URI.create(HOST + "/sitemap.xml")),
                Pattern.compile("hardware"), Pattern.compile("/produto/"), Set.of("loja.example"), Duration.ofSeconds(5), "PCPriceBot"),
                site, store, () -> matcher, new OfferValidation(Set.of("loja.example"), Duration.ofHours(72)), clock, () -> { });
    }

    private void standardSite() {
        site.page("/robots.txt", 200, "User-agent: *\nDisallow: /carrinho\nCrawl-delay: 10\n");
        site.page("/sitemap.xml", 200, """
                <sitemapindex><sitemap><loc>https://www.loja.example/sitemap/hardware.xml</loc></sitemap>
                <sitemap><loc>https://www.loja.example/sitemap/moveis.xml</loc></sitemap></sitemapindex>""");
        site.page("/sitemap/hardware.xml", 200, """
                <urlset>
                  <url><loc>https://www.loja.example/produto/1/placa-de-video</loc></url>
                  <url><loc>https://www.loja.example/produto/2/cadeira</loc></url>
                  <url><loc>https://www.loja.example/carrinho/produto/3</loc></url>
                  <url><loc>https://www.loja.example/categoria/hardware</loc></url>
                  <url><loc>https://outra-loja.example/produto/9</loc></url>
                </urlset>""");
        site.page("/produto/1/placa-de-video", 200, product("0824142335499", "MSI", "3899.90"));
        site.page("/produto/2/cadeira", 200, product("4006381333931", "Outra", "899.00"));
    }

    private static String product(String gtin, String brand, String price) {
        return """
                <script type="application/ld+json">{"@type":"Product","name":"Produto","brand":{"name":"%s"},"gtin13":"%s",
                "offers":{"@type":"Offer","priceCurrency":"BRL","price":"%s","availability":"https://schema.org/InStock"}}</script>"""
                .formatted(brand, gtin, price);
    }

    @Test
    void discoversMatchesAndPricesPolitely() {
        standardSite();
        StoreCrawler crawler = crawler();

        Duration afterRobots = crawler.step();
        assertThat(afterRobots).isEqualTo(Duration.ofSeconds(10)); // Crawl-delay is longer than our 5 s minimum
        crawler.step(); // sitemap index
        crawler.step(); // hardware sitemap (the furniture one is filtered out)
        assertThat(site.requested).doesNotContain("/sitemap/moveis.xml");
        // Only product URLs on the store's own host that robots.txt allows.
        assertThat(store.pages.keySet()).containsExactlyInAnyOrder(HOST + "/produto/1/placa-de-video", HOST + "/produto/2/cadeira");

        crawler.step();
        crawler.step();
        assertThat(store.offers).hasSize(1);
        StoredOffer offer = store.offers.values().iterator().next();
        assertThat(offer.componentId()).isEqualTo(gpu.id());
        assertThat(offer.priceBrl()).isEqualByComparingTo("3899.90");
        assertThat(offer.url()).isEqualTo(HOST + "/produto/1/placa-de-video");
        assertThat(store.history).hasSize(1);
        assertThat(store.pages.get(HOST + "/produto/1/placa-de-video").status()).isEqualTo(CrawlStore.Status.MATCHED);
        assertThat(store.pages.get(HOST + "/produto/2/cadeira").status()).isEqualTo(CrawlStore.Status.UNMATCHED);
        assertThat(site.requested).noneMatch(path -> path.startsWith("/carrinho"));
    }

    @Test
    void unchangedPagesAreNotDownloadedAgain() {
        standardSite();
        StoreCrawler crawler = crawler();
        for (int i = 0; i < 5; i++) {
            crawler.step();
        }
        site.notModified("/produto/1/placa-de-video");
        clock.advance(StoreCrawler.MATCHED_REVISIT.plusMinutes(1));

        crawler.step();

        assertThat(site.conditionalRequests).contains("/produto/1/placa-de-video");
        assertThat(store.offers).hasSize(1); // still there, not re-validated from an empty body
    }

    @Test
    void aStoreThatRefusesTheBotIsLeftAlone() {
        standardSite();
        site.page("/robots.txt", 403, "");
        StoreCrawler crawler = crawler();

        Duration wait = crawler.step();
        crawler.step();

        assertThat(wait).isEqualTo(Duration.ofSeconds(5));
        assertThat(crawler.step()).isGreaterThan(Duration.ofHours(23)); // paused for a day
        assertThat(site.requested).containsExactly("/robots.txt");
        assertThat(crawler.status().lastProblem()).contains("blocks robots");
    }

    @Test
    void tooManyRequestsPausesTheStore() {
        standardSite();
        StoreCrawler crawler = crawler();
        crawler.step();
        crawler.step();
        crawler.step();
        site.page("/produto/1/placa-de-video", 429, "");
        site.page("/produto/2/cadeira", 429, "");

        crawler.step();

        Duration wait = crawler.step();
        assertThat(wait).isGreaterThan(Duration.ofMinutes(55));
        assertThat(store.offers).isEmpty();
    }

    @Test
    void aSuspiciousPriceIsHeldBack() {
        standardSite();
        StoreCrawler crawler = crawler();
        for (int i = 0; i < 5; i++) {
            crawler.step();
        }
        site.page("/produto/1/placa-de-video", 200, product("0824142335499", "MSI", "999.00"));
        clock.advance(StoreCrawler.MATCHED_REVISIT.plusMinutes(1));

        crawler.step();

        assertThat(store.offers.values().iterator().next().priceBrl()).isEqualByComparingTo("3899.90");
        assertThat(crawler.status().offersRejected()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------------ fakes

    static final class FakeSite implements PageFetcher {
        private final Map<String, Response> pages = new HashMap<>();
        final List<String> requested = new ArrayList<>();
        final List<String> conditionalRequests = new ArrayList<>();
        private final Set<String> unchanged = new java.util.HashSet<>();

        void page(String path, int status, String body) {
            pages.put(path, new Response(status, body.getBytes(StandardCharsets.UTF_8), "\"v1\"", null, null));
        }

        void notModified(String path) {
            unchanged.add(path);
        }

        @Override
        public Response get(URI uri, String etag, String lastModified) {
            String path = uri.getRawPath();
            requested.add(path);
            if (etag != null) {
                conditionalRequests.add(path);
                if (unchanged.contains(path)) {
                    return new Response(304, new byte[0], etag, null, null);
                }
            }
            return pages.getOrDefault(path, new Response(404, new byte[0], null, null, null));
        }
    }

    static final class MemoryStore implements CrawlStore {
        final Map<String, Page> pages = new LinkedHashMap<>();
        private final Map<String, Instant> due = new HashMap<>();
        final Map<String, StoredOffer> offers = new HashMap<>();
        final List<StoredOffer> history = new ArrayList<>();

        @Override
        public void discover(String storeId, List<String> urls) {
            for (String url : urls) {
                if (!pages.containsKey(url)) {
                    pages.put(url, new Page(storeId, url, Status.PENDING, null, null, null, 0));
                    due.put(url, Instant.EPOCH);
                }
            }
        }

        @Override
        public Optional<Page> nextDue(String storeId, Instant now) {
            return pages.values().stream()
                    .filter(page -> page.status() != Status.DISALLOWED && !due.get(page.url()).isAfter(now))
                    .sorted((a, b) -> Integer.compare(rank(a), rank(b)))
                    .findFirst();
        }

        private static int rank(Page page) {
            return page.status() == Status.MATCHED ? 0 : page.status() == Status.PENDING ? 1 : 2;
        }

        @Override
        public void save(Page page, Instant fetchedAt, Instant nextFetchAt) {
            pages.put(page.url(), page);
            due.put(page.url(), nextFetchAt);
        }

        @Override
        public Map<Status, Integer> counts(String storeId) {
            Map<Status, Integer> counts = new EnumMap<>(Status.class);
            pages.values().forEach(page -> counts.merge(page.status(), 1, Integer::sum));
            return counts;
        }

        @Override
        public List<BigDecimal> otherStorePrices(UUID componentId, String storeId, Instant since) {
            return List.of();
        }

        @Override
        public Optional<BigDecimal> currentPrice(UUID componentId, String storeId) {
            return Optional.ofNullable(offers.get(componentId + storeId)).map(StoredOffer::priceBrl);
        }

        @Override
        public void upsertOffer(StoredOffer offer) {
            offers.put(offer.componentId() + offer.storeId(), offer);
        }

        @Override
        public void removeOffer(UUID componentId, String storeId) {
            offers.remove(componentId + storeId);
        }

        @Override
        public void observe(StoredOffer offer) {
            history.add(offer);
        }

        final Map<String, String> titles = new HashMap<>();

        @Override
        public void annotate(String storeId, String url, String listingTitle, String matchMethod) {
            titles.put(url, listingTitle + " | " + matchMethod);
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
