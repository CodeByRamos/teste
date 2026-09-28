package app.platform.infra.pricing.feed;

import app.platform.hardware.HardwareComponent;
import app.platform.pricing.Offer;
import app.platform.pricing.PriceProvider;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Real prices imported from store feeds, served from memory. Offers older than {@code maxAge} are withheld at read
 * time, so a feed that stops updating stops showing prices instead of showing old ones.
 */
public final class StoreFeedPriceProvider implements PriceProvider {

    public static final String ID = "store-feed";

    private final Duration maxAge;
    private final Clock clock;
    private final AtomicLong version = new AtomicLong();
    private volatile Map<UUID, List<StoredOffer>> byComponent = Map.of();

    public StoreFeedPriceProvider(Duration maxAge, Clock clock) {
        this.maxAge = maxAge;
        this.clock = clock;
    }

    public void replaceAll(List<StoredOffer> offers) {
        byComponent = offers.stream().collect(Collectors.groupingBy(StoredOffer::componentId, Collectors.toUnmodifiableList()));
        version.incrementAndGet();
    }

    public int size() {
        return byComponent.values().stream().mapToInt(List::size).sum();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Offer> offersFor(HardwareComponent component) {
        var cutoff = clock.instant().minus(maxAge);
        return byComponent.getOrDefault(component.id(), List.of()).stream()
                .filter(offer -> offer.observedAt().isAfter(cutoff))
                .map(offer -> new Offer(offer.componentId(), ID, offer.storeName(), offer.priceBrl(), offer.url(),
                        offer.availability(), offer.observedAt(), Offer.Kind.REAL))
                .toList();
    }

    /** Also moves every hour, so derived caches drop offers that became stale without a new import. */
    @Override
    public long version() {
        return version.get() * 1_000_000 + clock.instant().getEpochSecond() / 3600;
    }
}
