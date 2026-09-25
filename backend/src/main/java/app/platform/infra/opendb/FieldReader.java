package app.platform.infra.opendb;

import app.platform.hardware.DataIssue;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads fields from one OpenDB record, validating each value and recording issues instead of failing.
 * Paths use dots for nesting ("specifications.tdp"). Implausible values become {@code null}.
 */
final class FieldReader {

    private final JsonNode root;
    private final List<DataIssue> issues = new ArrayList<>();
    private int criticalFields;
    private int criticalProblems;

    FieldReader(JsonNode root) {
        this.root = root;
    }

    List<DataIssue> issues() {
        return issues;
    }

    double qualityScore() {
        return criticalFields == 0 ? 1.0 : (criticalFields - criticalProblems) / (double) criticalFields;
    }

    JsonNode node(String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        return current == null || current.isNull() || current.isMissingNode() ? null : current;
    }

    String text(String path) {
        JsonNode value = node(path);
        if (value == null || !value.isString()) {
            return null;
        }
        String text = value.stringValue().trim();
        return text.isEmpty() ? null : text;
    }

    Integer integer(String path) {
        JsonNode value = node(path);
        return value != null && value.isNumber() ? (int) Math.round(value.doubleValue()) : null;
    }

    Double decimal(String path) {
        JsonNode value = node(path);
        return value != null && value.isNumber() ? value.doubleValue() : null;
    }

    Boolean bool(String path) {
        JsonNode value = node(path);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    Set<String> texts(String path) {
        JsonNode value = node(path);
        Set<String> result = new LinkedHashSet<>();
        if (value != null && value.isArray()) {
            for (JsonNode item : value) {
                if (item.isString() && !item.stringValue().isBlank()) {
                    result.add(item.stringValue().trim());
                }
            }
        }
        return result;
    }

    /** Integer within [min, max]; outside values are discarded and reported. */
    Integer integerInRange(String path, int min, int max, boolean critical) {
        Integer value = integer(path);
        if (value != null && (value < min || value > max)) {
            report(path, DataIssue.Kind.OUT_OF_RANGE, "Valor " + value + " fora do intervalo plausível " + min + "–" + max, critical);
            value = null;
        } else if (value == null && critical) {
            report(path, DataIssue.Kind.MISSING, null, true);
        } else if (critical) {
            criticalFields++;
        }
        return value;
    }

    Double decimalInRange(String path, double min, double max, boolean critical) {
        Double value = decimal(path);
        if (value != null && (value < min || value > max)) {
            report(path, DataIssue.Kind.OUT_OF_RANGE, "Valor " + value + " fora do intervalo plausível " + min + "–" + max, critical);
            value = null;
        } else if (value == null && critical) {
            report(path, DataIssue.Kind.MISSING, null, true);
        } else if (critical) {
            criticalFields++;
        }
        return value;
    }

    /** Marks a critical field as present or missing, for values read with custom logic. */
    <T> T critical(String path, T value) {
        if (value == null || value instanceof Set<?> set && set.isEmpty() || value instanceof List<?> list && list.isEmpty()) {
            report(path, DataIssue.Kind.MISSING, null, true);
            return value instanceof Set<?> || value instanceof List<?> ? value : null;
        }
        criticalFields++;
        return value;
    }

    void unrecognized(String path, String detail, boolean critical) {
        issues.add(new DataIssue(path, DataIssue.Kind.UNRECOGNIZED, detail));
        if (critical) {
            criticalFields++;
            criticalProblems++;
        }
    }

    void normalized(String path, String detail) {
        issues.add(new DataIssue(path, DataIssue.Kind.NORMALIZED, detail));
    }

    private void report(String path, DataIssue.Kind kind, String detail, boolean critical) {
        issues.add(new DataIssue(path, kind, detail));
        if (critical) {
            criticalFields++;
            criticalProblems++;
        }
    }
}
