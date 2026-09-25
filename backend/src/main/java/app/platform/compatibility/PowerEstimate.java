package app.platform.compatibility;

/**
 * Estimated electrical demand of a build.
 *
 * @param estimatedLoadWatts   sustained draw under heavy load
 * @param recommendedPsuWatts  suggested power supply rating, with headroom for spikes and aging
 * @param complete             false when CPU or GPU power was unknown, so the estimate is a lower bound
 */
public record PowerEstimate(int estimatedLoadWatts, int recommendedPsuWatts, boolean complete) {
}
