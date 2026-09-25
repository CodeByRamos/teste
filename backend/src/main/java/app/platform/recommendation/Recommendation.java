package app.platform.recommendation;

import app.platform.compatibility.BuildParts;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The engine's choice of parts. Presentation (prices per item, explanations, alternatives) is
 * assembled separately so the same choice can be re-evaluated later.
 *
 * @param totalBrl     price of the parts that must be bought (owned parts cost nothing)
 * @param withinBudget false when even the cheapest compatible build costs more than the budget
 * @param notes        plain-language notes about decisions the person should know
 */
public record Recommendation(
        BuildParts parts,
        Set<UUID> ownedIds,
        RequirementProfile profile,
        BigDecimal totalBrl,
        boolean withinBudget,
        List<String> notes) {

    public Recommendation {
        ownedIds = Set.copyOf(ownedIds);
        notes = List.copyOf(notes);
    }
}
