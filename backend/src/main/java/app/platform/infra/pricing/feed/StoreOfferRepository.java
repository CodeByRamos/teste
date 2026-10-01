package app.platform.infra.pricing.feed;

import app.platform.pricing.Gtin;
import app.platform.pricing.Offer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StoreOfferRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final JsonMapper json;

    public StoreOfferRepository(JdbcTemplate jdbc, TransactionTemplate transactions, JsonMapper json) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.json = json;
    }

    /** Normalized GTIN-14 → components carrying it (EAN, UPC and GTIN identifiers from the catalog source). */
    Map<String, Set<UUID>> gtinIndex() {
        Map<String, Set<UUID>> index = new HashMap<>();
        jdbc.query("select component_id, value from component_identifier where type in ('ean', 'upc', 'gtin')", row -> {
            UUID component = row.getObject(1, UUID.class);
            Gtin.normalize(row.getString(2)).ifPresent(gtin -> index.computeIfAbsent(gtin, key -> new HashSet<>()).add(component));
        });
        return index;
    }

    /** Normalized MPN → components carrying it. */
    Map<String, Set<UUID>> mpnIndex() {
        Map<String, Set<UUID>> index = new HashMap<>();
        jdbc.query("select component_id, value from component_identifier where type = 'mpn'", row -> {
            String mpn = CatalogMatcher.normalizeMpn(row.getString(2));
            if (mpn != null) {
                index.computeIfAbsent(mpn, key -> new HashSet<>()).add(row.getObject(1, UUID.class));
            }
        });
        return index;
    }

    /** Per store: name, number of offers and the most recent observation, for attribution pages. */
    public record StoreSummary(String storeId, String storeName, int offers, java.time.Instant latestObservation) {
    }

    public List<StoreSummary> storeSummaries() {
        return jdbc.query("""
                select store_id, max(store_name), count(*), max(observed_at) from store_offer group by store_id order by 2
                """, (row, index) -> new StoreSummary(row.getString(1), row.getString(2), row.getInt(3), row.getTimestamp(4).toInstant()));
    }

    public List<StoredOffer> all() {
        return jdbc.query("""
                select component_id, store_id, store_name, price_brl, url, availability, observed_at from store_offer
                """, (row, index) -> new StoredOffer(row.getObject(1, UUID.class), row.getString(2), row.getString(3),
                row.getBigDecimal(4), row.getString(5), Offer.Availability.valueOf(row.getString(6)),
                row.getTimestamp(7).toInstant()));
    }

    /** Replaces everything known about one store with this import, and records the import, atomically. */
    void replaceStore(String storeId, List<StoredOffer> offers, FeedImporter.Summary summary) {
        transactions.executeWithoutResult(status -> {
            Long importId = jdbc.queryForObject("""
                    insert into price_feed_import (store_id, rows_read, rows_matched, rows_accepted, rejections)
                    values (?, ?, ?, ?, cast(? as jsonb)) returning id
                    """, Long.class, storeId, summary.rowsRead(), summary.rowsMatched(), summary.accepted(),
                    json.writeValueAsString(summary.rejections()));
            jdbc.update("delete from store_offer where store_id = ?", storeId);
            jdbc.batchUpdate("""
                    insert into store_offer (component_id, store_id, store_name, price_brl, url, availability, observed_at, import_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, offers, 500, (statement, offer) -> {
                statement.setObject(1, offer.componentId());
                statement.setString(2, offer.storeId());
                statement.setString(3, offer.storeName());
                statement.setBigDecimal(4, offer.priceBrl());
                statement.setString(5, offer.url());
                statement.setString(6, offer.availability().name());
                statement.setTimestamp(7, Timestamp.from(offer.observedAt()));
                statement.setLong(8, importId);
            });
            jdbc.batchUpdate("""
                    insert into price_observation (component_id, store_id, store_name, price_brl, availability, observed_at)
                    values (?, ?, ?, ?, ?, ?)
                    """, offers, 500, (statement, offer) -> {
                statement.setObject(1, offer.componentId());
                statement.setString(2, offer.storeId());
                statement.setString(3, offer.storeName());
                statement.setBigDecimal(4, offer.priceBrl());
                statement.setString(5, offer.availability().name());
                statement.setTimestamp(6, Timestamp.from(offer.observedAt()));
            });
        });
    }
}
