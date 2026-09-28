package app.platform.infra.pricing.feed;

import app.platform.pricing.Offer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A validated store offer as persisted. */
public record StoredOffer(UUID componentId, String storeId, String storeName, BigDecimal priceBrl, String url,
                          Offer.Availability availability, Instant observedAt) {
}
