package app.platform.pricing;

import app.platform.hardware.HardwareComponent;

import java.util.List;

/**
 * A source of prices (a store feed, an affiliate API, or the development mock).
 * Hardware specifications never come from price providers, and prices never come from hardware sources.
 */
public interface PriceProvider {

    String id();

    /** Current offers for a component; empty when the provider does not sell or know it. */
    List<Offer> offersFor(HardwareComponent component);
}
