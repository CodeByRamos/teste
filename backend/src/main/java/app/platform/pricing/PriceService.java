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

    public List<Offer> offersFor(HardwareComponent component) {
        return providers.stream()
                .flatMap(provider -> provider.offersFor(component).stream())
                .sorted(Comparator.comparing(Offer::priceBrl))
                .toList();
    }

    /** Lowest price among offers that are not known to be out of stock. */
    public Optional<Offer> bestOffer(HardwareComponent component) {
        return offersFor(component).stream().filter(Offer::purchasable).findFirst();
    }
}
