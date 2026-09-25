package app.platform.builds;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A build the person chose to keep. {@code view} is the exact document that was shown when it was saved
 * (prices and explanations at that moment), so a saved build never silently changes.
 *
 * @param id random and unguessable; it is the only credential to read the build until accounts exist
 */
public record SavedBuild(
        UUID id,
        Instant createdAt,
        String title,
        String requestJson,
        List<SavedItem> items,
        BigDecimal totalBrl,
        boolean pricesAreExamples,
        String engineVersion,
        String catalogVersion,
        String viewJson) {

    public SavedBuild {
        items = List.copyOf(items);
    }

    /** Reference kept per part so saved builds can later power "Meu PC", upgrades and price tracking. */
    public record SavedItem(UUID componentId, String category, String source, String externalId, boolean owned,
                            BigDecimal priceBrl, String priceKind) {
    }
}
