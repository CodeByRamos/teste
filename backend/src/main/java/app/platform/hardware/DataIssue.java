package app.platform.hardware;

/**
 * A problem detected in a source record. External data is community maintained and is never
 * assumed correct just because a field is present.
 */
public record DataIssue(String field, Kind kind, String detail) {

    public enum Kind {
        /** Field needed by the platform is absent. */
        MISSING,
        /** Value exists but is physically implausible; it was discarded. */
        OUT_OF_RANGE,
        /** Value is not one we can interpret; it was discarded. */
        UNRECOGNIZED,
        /** Value was rewritten to a canonical form (e.g. socket alias). Kept for auditing. */
        NORMALIZED
    }

    public boolean discardsValue() {
        return kind != Kind.NORMALIZED;
    }
}
