package app.platform.hardware;

import java.util.List;

/**
 * Completeness of the fields the platform relies on for a component.
 *
 * @param score  share of critical fields that are present and plausible, from 0 to 1
 * @param issues everything detected while reading the record
 */
public record DataQuality(double score, List<DataIssue> issues) {

    public DataQuality {
        issues = List.copyOf(issues);
    }

    public boolean hasProblemWith(String field) {
        return issues.stream().anyMatch(issue -> issue.field().equals(field) && issue.discardsValue());
    }
}
