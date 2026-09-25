package app.platform.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A price for a component at one store, in Brazilian reais.
 *
 * @param url      product page, or {@code null} when the provider has none (never fabricated)
 * @param kind     whether this is a real observed price or an example used during development
 */
public record Offer(
        UUID componentId,
        String providerId,
        String storeName,
        BigDecimal priceBrl,
        String url,
        Availability availability,
        Instant observedAt,
        Kind kind) {

    public enum Kind {
        /** Observed at a store by a real provider. */
        REAL,
        /** Fictitious price for development. Must always be labeled as such to users. */
        EXAMPLE
    }

    public enum Availability {
        IN_STOCK, OUT_OF_STOCK, UNKNOWN
    }

    public Offer {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(priceBrl, "priceBrl");
        Objects.requireNonNull(kind, "kind");
        if (priceBrl.signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive: " + priceBrl);
        }
    }

    public boolean purchasable() {
        return availability != Availability.OUT_OF_STOCK;
    }
}
