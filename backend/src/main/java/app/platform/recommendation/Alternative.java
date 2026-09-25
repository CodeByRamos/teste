package app.platform.recommendation;

import app.platform.hardware.HardwareComponent;

import java.math.BigDecimal;

/**
 * A swap the person could make for one part while keeping the rest of the build compatible.
 *
 * @param priceDeltaBrl alternative price minus current price (negative = saves money)
 * @param impact        plain-language consequence of the swap
 */
public record Alternative(HardwareComponent component, BigDecimal priceBrl, BigDecimal priceDeltaBrl, Direction direction, String impact) {

    public enum Direction {
        CHEAPER, BETTER
    }
}
