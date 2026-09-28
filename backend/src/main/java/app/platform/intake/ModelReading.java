package app.platform.intake;

import app.platform.recommendation.TargetResolution;
import app.platform.recommendation.UseCase;

import java.math.BigDecimal;
import java.util.Set;

/**
 * A language model's reading of a free-text request: structured fields only. There is deliberately no free text
 * here, so nothing the model writes is ever shown to people or fed back into decisions as instructions.
 *
 * @param budgetBrl  {@code null} when the text states no amount
 * @param resolution {@code null} when the text does not say
 */
public record ModelReading(BigDecimal budgetBrl, Set<UseCase> useCases, TargetResolution resolution, boolean planUpgrades,
                           boolean mentionsOwnedParts) {
    public ModelReading {
        useCases = useCases == null ? Set.of() : Set.copyOf(useCases);
    }
}
