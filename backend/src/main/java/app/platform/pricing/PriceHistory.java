package app.platform.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Prices seen over time (feeds and the price bot), for comparisons such as "lowest in the last 30 days". */
public interface PriceHistory {

    PriceHistory NONE = new PriceHistory() {
        @Override
        public Optional<LowestPrice> lowestSince(UUID componentId, Instant since) {
            return Optional.empty();
        }

        @Override
        public List<DailyLow> dailyLows(UUID componentId, Instant since) {
            return List.of();
        }
    };

    record LowestPrice(BigDecimal priceBrl, String storeName, Instant observedAt) {
    }

    /** Lowest price a store showed on one day (Brazilian calendar day). */
    record DailyLow(LocalDate day, String storeName, BigDecimal priceBrl) {
    }

    Optional<LowestPrice> lowestSince(UUID componentId, Instant since);

    /** One point per store per day, oldest first, for price charts. */
    List<DailyLow> dailyLows(UUID componentId, Instant since);
}
