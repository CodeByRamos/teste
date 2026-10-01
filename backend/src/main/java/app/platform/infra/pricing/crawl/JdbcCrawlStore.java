package app.platform.infra.pricing.crawl;

import app.platform.infra.pricing.feed.StoredOffer;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class JdbcCrawlStore implements CrawlStore {

    private final JdbcTemplate jdbc;

    public JdbcCrawlStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void discover(String storeId, List<String> urls) {
        jdbc.batchUpdate("insert into crawl_page (store_id, url) values (?, ?) on conflict do nothing", urls, 1000,
                (statement, url) -> {
                    statement.setString(1, storeId);
                    statement.setString(2, url);
                });
    }

    @Override
    public Optional<Page> nextDue(String storeId, Instant now) {
        return jdbc.query("""
                select store_id, url, status, etag, last_modified, component_id, failures from crawl_page
                where store_id = ? and next_fetch_at <= ? and status <> 'disallowed'
                order by (status = 'matched') desc, (status = 'pending') desc, next_fetch_at
                limit 1
                """, (row, index) -> new Page(row.getString(1), row.getString(2),
                Status.valueOf(row.getString(3).toUpperCase(Locale.ROOT)), row.getString(4), row.getString(5),
                row.getObject(6, UUID.class), row.getInt(7)), storeId, Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public void save(Page page, Instant fetchedAt, Instant nextFetchAt) {
        jdbc.update("""
                update crawl_page set status = ?, etag = ?, last_modified = ?, component_id = ?, failures = ?,
                    last_fetched_at = ?, next_fetch_at = ?
                where store_id = ? and url = ?
                """, page.status().name().toLowerCase(Locale.ROOT), page.etag(), page.lastModified(), page.componentId(),
                page.failures(), Timestamp.from(fetchedAt), Timestamp.from(nextFetchAt), page.storeId(), page.url());
    }

    @Override
    public Map<Status, Integer> counts(String storeId) {
        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        jdbc.query("select status, count(*) from crawl_page where store_id = ? group by status",
                row -> {
                    counts.put(Status.valueOf(row.getString(1).toUpperCase(Locale.ROOT)), row.getInt(2));
                }, storeId);
        return counts;
    }

    @Override
    public List<BigDecimal> otherStorePrices(UUID componentId, String storeId, Instant since) {
        return jdbc.queryForList("""
                select price_brl from store_offer where component_id = ? and store_id <> ? and observed_at >= ?
                """, BigDecimal.class, componentId, storeId, Timestamp.from(since));
    }

    @Override
    public Optional<BigDecimal> currentPrice(UUID componentId, String storeId) {
        return jdbc.queryForList("select price_brl from store_offer where component_id = ? and store_id = ?",
                BigDecimal.class, componentId, storeId).stream().findFirst();
    }

    @Override
    public void upsertOffer(StoredOffer offer) {
        jdbc.update("""
                insert into store_offer (component_id, store_id, store_name, price_brl, url, availability, observed_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (component_id, store_id) do update set store_name = excluded.store_name,
                    price_brl = excluded.price_brl, url = excluded.url, availability = excluded.availability,
                    observed_at = excluded.observed_at, import_id = null
                """, offer.componentId(), offer.storeId(), offer.storeName(), offer.priceBrl(), offer.url(),
                offer.availability().name(), Timestamp.from(offer.observedAt()));
    }

    @Override
    public void removeOffer(UUID componentId, String storeId) {
        jdbc.update("delete from store_offer where component_id = ? and store_id = ?", componentId, storeId);
    }

    @Override
    public void observe(StoredOffer offer) {
        jdbc.update("""
                insert into price_observation (component_id, store_id, store_name, price_brl, availability, observed_at)
                values (?, ?, ?, ?, ?, ?)
                """, offer.componentId(), offer.storeId(), offer.storeName(), offer.priceBrl(), offer.availability().name(),
                Timestamp.from(offer.observedAt()));
    }
}
