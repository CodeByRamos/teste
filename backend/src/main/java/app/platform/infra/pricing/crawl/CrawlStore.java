package app.platform.infra.pricing.crawl;

import app.platform.infra.pricing.feed.StoredOffer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent crawl state and the bot's writes to offers and price history. */
public interface CrawlStore {

    enum Status { PENDING, MATCHED, UNMATCHED, NOT_PRODUCT, DISALLOWED, ERROR }

    record Page(String storeId, String url, Status status, String etag, String lastModified, UUID componentId, int failures) {
    }

    /** Adds URLs not seen before as pending; known URLs are left untouched. */
    void discover(String storeId, List<String> urls);

    /** Next page to visit: catalog matches first, then never-visited pages, then the rest, oldest due first. */
    Optional<Page> nextDue(String storeId, Instant now);

    void save(Page page, Instant fetchedAt, Instant nextFetchAt);

    Map<Status, Integer> counts(String storeId);

    /** Current prices of this component at other stores (for the outlier check). */
    List<BigDecimal> otherStorePrices(UUID componentId, String storeId, Instant since);

    /** This store's current price for the component, if any (for the sudden-drop check). */
    Optional<BigDecimal> currentPrice(UUID componentId, String storeId);

    void upsertOffer(StoredOffer offer);

    void removeOffer(UUID componentId, String storeId);

    /** Appends to the price history. */
    void observe(StoredOffer offer);

    /** Records what the store called the product and how it matched (null when unmatched), for audits. */
    void annotate(String storeId, String url, String listingTitle, String matchMethod);
}
