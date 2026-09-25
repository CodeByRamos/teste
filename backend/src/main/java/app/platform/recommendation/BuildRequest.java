package app.platform.recommendation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What the person asked for.
 *
 * @param useCases           everything they want to do; {@code primaryUse} breaks ties when budget is tight
 * @param resolution         only relevant for games; {@code null} means Full HD
 * @param ownedComponentIds  parts they already have and want to reuse (cost nothing)
 */
public record BuildRequest(
        BigDecimal budgetBrl,
        Set<UseCase> useCases,
        UseCase primaryUse,
        TargetResolution resolution,
        List<UUID> ownedComponentIds) {

    public static final BigDecimal MIN_BUDGET = new BigDecimal("1500");
    public static final BigDecimal MAX_BUDGET = new BigDecimal("100000");

    public BuildRequest {
        Objects.requireNonNull(budgetBrl, "budgetBrl");
        useCases = useCases == null || useCases.isEmpty() ? Set.of(UseCase.OFFICE_STUDY) : Set.copyOf(useCases);
        if (primaryUse != null && !useCases.contains(primaryUse)) {
            throw new IllegalArgumentException("primaryUse must be one of useCases");
        }
        resolution = resolution == null ? TargetResolution.FULL_HD : resolution;
        ownedComponentIds = ownedComponentIds == null ? List.of() : List.copyOf(ownedComponentIds);
    }

    public boolean includesGaming() {
        return useCases.stream().anyMatch(UseCase::isGaming);
    }
}
