package app.platform.infra.pricing.feed;

import java.util.HashMap;
import java.util.Map;

/**
 * Column names of a store feed. {@code GENERIC} is this platform's own CSV layout; {@code AWIN} follows the Awin
 * product feed. Any column can be overridden per feed in configuration, since networks let publishers pick columns.
 */
public enum FeedFormat {

    GENERIC(Map.of(
            Column.GTIN, "gtin", Column.MPN, "mpn", Column.BRAND, "brand", Column.PRICE, "price_brl", Column.URL, "url",
            Column.AVAILABILITY, "availability", Column.OBSERVED_AT, "observed_at", Column.CURRENCY, "currency")),
    AWIN(Map.of(
            Column.GTIN, "ean", Column.MPN, "mpn", Column.BRAND, "brand_name", Column.PRICE, "search_price",
            Column.URL, "aw_deep_link", Column.AVAILABILITY, "in_stock", Column.OBSERVED_AT, "last_updated",
            Column.CURRENCY, "currency"));

    public enum Column { GTIN, MPN, BRAND, PRICE, URL, AVAILABILITY, OBSERVED_AT, CURRENCY }

    private final Map<Column, String> columns;

    FeedFormat(Map<Column, String> columns) {
        this.columns = columns;
    }

    public static FeedFormat of(String name) {
        return name == null || name.isBlank() ? GENERIC : valueOf(name.strip().toUpperCase(java.util.Locale.ROOT));
    }

    private static Column column(String key) {
        String folded = key.strip().toUpperCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
        for (Column column : Column.values()) {
            if (column.name().replace("_", "").equals(folded)) {
                return column;
            }
        }
        throw new IllegalStateException("Unknown feed column override: " + key);
    }

    /** Column names with per-feed overrides applied (keys: gtin, price, url, availability, observed-at, currency). */
    Map<Column, String> columns(Map<String, String> overrides) {
        Map<Column, String> result = new HashMap<>(columns);
        if (overrides != null) {
            // Keys arrive as "observed-at" from YAML or "observedat" from environment variables.
            overrides.forEach((key, value) -> result.put(column(key), value.strip()));
        }
        return result;
    }
}
