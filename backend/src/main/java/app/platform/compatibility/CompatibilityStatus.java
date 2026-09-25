package app.platform.compatibility;

/** Ordered from least to most severe. */
public enum CompatibilityStatus {
    OK,
    WARNING,
    INCOMPATIBLE;

    public CompatibilityStatus worst(CompatibilityStatus other) {
        return other.ordinal() > ordinal() ? other : this;
    }
}
