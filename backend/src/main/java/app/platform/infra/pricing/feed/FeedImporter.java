package app.platform.infra.pricing.feed;

import app.platform.catalog.Catalog;
import app.platform.pricing.Gtin;
import app.platform.pricing.Offer;
import app.platform.pricing.OfferValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Imports one store's product feed: reads rows, matches them to catalog components by GTIN only, validates each
 * offer ({@link OfferValidation}) and replaces the store's offers. Nothing is guessed: rows that cannot be matched
 * or parsed unambiguously are skipped and counted.
 */
public final class FeedImporter {

    private static final Logger log = LoggerFactory.getLogger(FeedImporter.class);
    /** A feed that suddenly yields far fewer valid offers than the store had is treated as broken, not applied. */
    static final double MIN_RETAINED_SHARE = 0.2;
    static final int MIN_OFFERS_FOR_RETENTION_CHECK = 20;
    private static final String BYTE_ORDER_MARK = "﻿";

    /**
     * @param id        stable store identifier (e.g. "kabum")
     * @param storeName name shown to people
     */
    public record Feed(String id, String storeName, FeedFormat format, Map<String, String> columns, char delimiter,
                       OfferValidation validation) {
    }

    /**
     * @param matchedByMpn rows matched through MPN + brand because they had no usable GTIN
     * @param applied      false when the feed looked broken and the store's previous offers were kept
     */
    public record Summary(String storeId, int rowsRead, int rowsMatched, int matchedByMpn, int accepted,
                          Map<String, Integer> rejections, boolean applied) {
    }


    private final StoreOfferRepository repository;
    private final StoreFeedPriceProvider provider;
    private final Supplier<Catalog> catalogs;
    private final Clock clock;

    public FeedImporter(StoreOfferRepository repository, StoreFeedPriceProvider provider, Supplier<Catalog> catalogs, Clock clock) {
        this.repository = repository;
        this.provider = provider;
        this.catalogs = catalogs;
        this.clock = clock;
    }

    /** Loads stored offers into the provider (at startup). */
    public void loadStored() {
        provider.replaceAll(repository.all());
    }

    public Summary importFeed(Feed feed, InputStream input) throws IOException {
        Instant now = clock.instant();
        CsvReader csv = new CsvReader(new InputStreamReader(input, StandardCharsets.UTF_8), feed.delimiter());
        List<String> header = csv.next();
        if (header == null) {
            throw new IllegalArgumentException("Feed vazio.");
        }
        Map<FeedFormat.Column, Integer> positions = positions(header, feed.format().columns(feed.columns()));

        CatalogMatcher matcher = CatalogMatcher.load(repository, catalogs.get());
        List<StoredOffer> existing = repository.all();
        Map<UUID, BigDecimal> previous = new HashMap<>();
        Map<UUID, List<BigDecimal>> otherStores = new HashMap<>();
        for (StoredOffer offer : existing) {
            if (offer.storeId().equals(feed.id())) {
                previous.put(offer.componentId(), offer.priceBrl());
            } else if (offer.observedAt().isAfter(now.minus(feed.validation().maxAge()))) {
                otherStores.computeIfAbsent(offer.componentId(), key -> new ArrayList<>()).add(offer.priceBrl());
            }
        }

        Map<String, Integer> rejections = new TreeMap<>();
        Map<UUID, StoredOffer> best = new HashMap<>();
        int read = 0;
        int matched = 0;
        int byMpn = 0;
        List<String> row;
        while ((row = csv.next()) != null) {
            read++;
            if (row.size() < header.size()) {
                count(rejections, "BAD_ROW");
                continue;
            }
            Optional<CatalogMatcher.Match> match = matcher.match(cell(row, positions, FeedFormat.Column.GTIN),
                    cell(row, positions, FeedFormat.Column.MPN), cell(row, positions, FeedFormat.Column.BRAND), null);
            if (match.isEmpty()) {
                continue; // not a PC part in our catalog (or ambiguous): expected for most rows of a store feed
            }
            matched++;
            if (match.get().method() != CatalogMatcher.Method.GTIN) {
                byMpn++;
            }
            List<UUID> components = List.of(match.get().componentId());
            String currency = cell(row, positions, FeedFormat.Column.CURRENCY);
            if (currency != null && !currency.isBlank() && !currency.strip().equalsIgnoreCase("BRL")) {
                count(rejections, "WRONG_CURRENCY");
                continue;
            }
            BigDecimal price = FeedValues.price(cell(row, positions, FeedFormat.Column.PRICE));
            if (price == null) {
                count(rejections, "UNREADABLE_PRICE");
                continue;
            }
            Instant observed = FeedValues.instant(cell(row, positions, FeedFormat.Column.OBSERVED_AT));
            StoredOffer offer = new StoredOffer(components.getFirst(), feed.id(), feed.storeName(), price,
                    Optional.ofNullable(cell(row, positions, FeedFormat.Column.URL)).map(String::strip).orElse(null),
                    FeedValues.availability(cell(row, positions, FeedFormat.Column.AVAILABILITY)),
                    observed == null ? now : observed);
            best.merge(offer.componentId(), offer, FeedImporter::better);
        }

        List<StoredOffer> accepted = new ArrayList<>();
        for (StoredOffer offer : best.values()) {
            Optional<OfferValidation.Rejection> rejection = feed.validation().check(offer.priceBrl(), offer.url(),
                    offer.observedAt(), now, otherStores.getOrDefault(offer.componentId(), List.of()),
                    previous.get(offer.componentId()));
            if (rejection.isPresent()) {
                count(rejections, rejection.get().name());
            } else {
                accepted.add(offer);
            }
        }

        boolean broken = previous.size() >= MIN_OFFERS_FOR_RETENTION_CHECK
                && accepted.size() < previous.size() * MIN_RETAINED_SHARE;
        Summary summary = new Summary(feed.id(), read, matched, byMpn, accepted.size(), rejections, !broken);
        if (broken) {
            log.warn("Price feed {} not applied: {} valid offers against {} before; keeping previous offers",
                    feed.id(), accepted.size(), previous.size());
            return summary;
        }
        repository.replaceStore(feed.id(), accepted, summary);
        provider.replaceAll(repository.all());
        log.info("Price feed {}: {} rows, {} matched the catalog, {} accepted, rejected {}",
                feed.id(), read, matched, accepted.size(), rejections);
        return summary;
    }

    /** In stock beats unknown beats out of stock; then the lower price. */
    private static StoredOffer better(StoredOffer a, StoredOffer b) {
        int rankA = rank(a.availability());
        int rankB = rank(b.availability());
        if (rankA != rankB) {
            return rankA < rankB ? a : b;
        }
        return a.priceBrl().compareTo(b.priceBrl()) <= 0 ? a : b;
    }

    private static int rank(Offer.Availability availability) {
        return switch (availability) {
            case IN_STOCK -> 0;
            case UNKNOWN -> 1;
            case OUT_OF_STOCK -> 2;
        };
    }

    private static Map<FeedFormat.Column, Integer> positions(List<String> header, Map<FeedFormat.Column, String> names) {
        Map<String, Integer> byName = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            // The first header cell may carry a UTF-8 byte order mark.
            byName.putIfAbsent(header.get(i).replace(BYTE_ORDER_MARK, "").strip().toLowerCase(Locale.ROOT), i);
        }
        Map<FeedFormat.Column, Integer> positions = new EnumMap<>(FeedFormat.Column.class);
        names.forEach((column, name) -> {
            Integer position = byName.get(name.toLowerCase(Locale.ROOT));
            if (position != null) {
                positions.put(column, position);
            }
        });
        for (FeedFormat.Column required : List.of(FeedFormat.Column.GTIN, FeedFormat.Column.PRICE, FeedFormat.Column.URL)) {
            if (!positions.containsKey(required)) {
                throw new IllegalArgumentException("Coluna obrigatória ausente no feed: " + names.get(required));
            }
        }
        return positions;
    }

    private static String cell(List<String> row, Map<FeedFormat.Column, Integer> positions, FeedFormat.Column column) {
        Integer position = positions.get(column);
        return position == null || position >= row.size() ? null : row.get(position);
    }

    private static void count(Map<String, Integer> rejections, String reason) {
        rejections.merge(reason, 1, Integer::sum);
    }
}
