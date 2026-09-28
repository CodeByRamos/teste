package app.platform.pricing;

import app.platform.hardware.HardwareComponent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Combines all price providers. Prices are always resolved server-side, never accepted from clients. */
public final class PriceService {

    private final List<PriceProvider> providers;

    public PriceService(List<PriceProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    /**
     * Offers from lowest price. Real store offers take precedence: when a component has any, fictitious example
     * prices for it are left out, so a real price is never compared with an invented one.
     */
    public List<Offer> offersFor(HardwareComponent component) {
        List<Offer> all = providers.stream()
                .flatMap(provider -> provider.offersFor(component).stream())
                .sorted(Comparator.comparing(Offer::priceBrl))
                .toList();
        boolean anyReal = all.stream().anyMatch(offer -> offer.kind() == Offer.Kind.REAL);
        return anyReal ? all.stream().filter(offer -> offer.kind() == Offer.Kind.REAL).toList() : all;
    }

    /** Changes whenever any provider's prices change. */
    public long version() {
        return providers.stream().mapToLong(PriceProvider::version).sum();
    }

    /** Lowest price among offers that are not known to be out of stock. */
    public Optional<Offer> bestOffer(HardwareComponent component) {
        return offersFor(component).stream().filter(Offer::purchasable).findFirst();
    }
}
