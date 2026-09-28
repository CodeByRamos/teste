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

    /** MPNs this short are too likely to collide across products to match on. */
    static final int MIN_MPN_LENGTH = 5;

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

        Map<String, Set<UUID>> index = repository.gtinIndex();
        Map<String, Set<UUID>> mpnIndex = repository.mpnIndex();
        Catalog catalog = catalogs.get();
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
            Optional<String> gtin = Gtin.normalize(cell(row, positions, FeedFormat.Column.GTIN));
            List<UUID> components = gtin.map(code -> inCatalog(index.get(code), catalog)).orElse(List.of());
            if (components.isEmpty()) {
                // Second tier, for catalog parts without GTIN: exact MPN, unique in the catalog, same brand.
                components = byMpnAndBrand(cell(row, positions, FeedFormat.Column.MPN),
                        cell(row, positions, FeedFormat.Column.BRAND), mpnIndex, catalog);
                if (components.isEmpty()) {
                    continue; // not a PC part in our catalog: expected for most rows of a store feed
                }
                byMpn++;
            }
            matched++;
            if (components.size() > 1) {
                count(rejections, "AMBIGUOUS_GTIN");
                continue;
            }
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

    private static List<UUID> inCatalog(Set<UUID> ids, Catalog catalog) {
        return ids == null ? List.of() : ids.stream().filter(id -> catalog.find(id).isPresent()).toList();
    }

    private static List<UUID> byMpnAndBrand(String rawMpn, String brand, Map<String, Set<UUID>> mpnIndex, Catalog catalog) {
        String mpn = normalizeMpn(rawMpn);
        if (mpn == null || brand == null || brand.isBlank()) {
            return List.of();
        }
        List<UUID> candidates = inCatalog(mpnIndex.get(mpn), catalog);
        if (candidates.size() != 1) {
            return List.of();
        }
        String feedBrand = foldBrand(brand);
        boolean sameBrand = catalog.find(candidates.getFirst())
                .map(component -> component.info().manufacturer())
                .map(FeedImporter::foldBrand)
                .filter(manufacturer -> !manufacturer.isEmpty() && (manufacturer.contains(feedBrand) || feedBrand.contains(manufacturer)))
                .isPresent();
        return sameBrand ? candidates : List.of();
    }

    /** Uppercase without spaces; {@code null} when too short to identify a product. */
    static String normalizeMpn(String raw) {
        if (raw == null) {
            return null;
        }
        String mpn = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return mpn.length() >= MIN_MPN_LENGTH ? mpn : null;
    }

    private static String foldBrand(String brand) {
        return brand.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
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
