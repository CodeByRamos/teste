package app.platform.pricing;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Checks an offer from a store feed before it can reach anyone. Protects against broken feeds and against
 * manipulated prices or links: an offer that fails any rule is dropped and counted, never shown.
 *
 * @param allowedDomains hosts the store's links may point to (the store and, for affiliate feeds, the network's
 *                       redirect host); subdomains are accepted
 * @param maxAge         older observations are considered stale
 */
public record OfferValidation(Set<String> allowedDomains, Duration maxAge) {

    static final BigDecimal MIN_PRICE = new BigDecimal("10");
    static final BigDecimal MAX_PRICE = new BigDecimal("100000");
    /** Allowed distance from the median of the other stores' prices (needs at least two other stores). */
    static final double LOW_OUTLIER = 0.4;
    static final double HIGH_OUTLIER = 2.5;
    /** A drop this large against the same store's previous price is held back as suspicious. */
    static final double SUSPICIOUS_DROP = 0.4;
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(10);

    public OfferValidation {
        allowedDomains = allowedDomains.stream().map(domain -> domain.strip().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public enum Rejection {
        PRICE_OUT_OF_RANGE, INVALID_URL, DOMAIN_NOT_ALLOWED, FROM_THE_FUTURE, STALE, OUTLIER, SUSPICIOUS_DROP
    }

    /**
     * @param otherStores   current prices of the same component at other stores
     * @param previousPrice this store's previous price for the component, if any
     */
    public Optional<Rejection> check(BigDecimal price, String url, Instant observedAt, Instant now,
                                     List<BigDecimal> otherStores, BigDecimal previousPrice) {
        if (price == null || price.compareTo(MIN_PRICE) < 0 || price.compareTo(MAX_PRICE) > 0) {
            return Optional.of(Rejection.PRICE_OUT_OF_RANGE);
        }
        Optional<String> host = httpsHost(url);
        if (host.isEmpty()) {
            return Optional.of(Rejection.INVALID_URL);
        }
        if (allowedDomains.stream().noneMatch(domain -> host.get().equals(domain) || host.get().endsWith("." + domain))) {
            return Optional.of(Rejection.DOMAIN_NOT_ALLOWED);
        }
        if (observedAt.isAfter(now.plus(CLOCK_SKEW))) {
            return Optional.of(Rejection.FROM_THE_FUTURE);
        }
        if (observedAt.isBefore(now.minus(maxAge))) {
            return Optional.of(Rejection.STALE);
        }
        if (otherStores.size() >= 2) {
            double median = median(otherStores);
            double value = price.doubleValue();
            if (value < median * LOW_OUTLIER || value > median * HIGH_OUTLIER) {
                return Optional.of(Rejection.OUTLIER);
            }
        }
        if (previousPrice != null && price.doubleValue() < previousPrice.doubleValue() * SUSPICIOUS_DROP) {
            return Optional.of(Rejection.SUSPICIOUS_DROP);
        }
        return Optional.empty();
    }

    private static Optional<String> httpsHost(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                return Optional.empty();
            }
            return Optional.of(uri.getHost().toLowerCase(Locale.ROOT));
        } catch (java.net.URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static double median(List<BigDecimal> values) {
        double[] sorted = values.stream().mapToDouble(BigDecimal::doubleValue).sorted().toArray();
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }
}
