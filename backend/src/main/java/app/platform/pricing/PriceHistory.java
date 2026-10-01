package app.platform.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Prices seen over time (feeds and the price bot), for comparisons such as "lowest in the last 30 days". */
@FunctionalInterface
public interface PriceHistory {

    PriceHistory NONE = (componentId, since) -> Optional.empty();

    record LowestPrice(BigDecimal priceBrl, String storeName, Instant observedAt) {
    }

    Optional<LowestPrice> lowestSince(UUID componentId, Instant since);
}
