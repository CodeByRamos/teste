package app.platform.recommendation;

/** Monitor resolution the person plays at. Only matters for games. */
public enum TargetResolution {
    FULL_HD("Full HD (1080p)"),
    QHD("Quad HD (1440p)"),
    UHD_4K("4K (2160p)");

    private final String label;

    TargetResolution(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
