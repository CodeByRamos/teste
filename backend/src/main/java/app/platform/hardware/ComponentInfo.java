package app.platform.hardware;

import java.util.UUID;

/** Data every component has, regardless of category. */
public record ComponentInfo(
        UUID id,
        SourceRef source,
        ComponentCategory category,
        String name,
        String manufacturer,
        String series,
        String variant,
        Integer releaseYear,
        DataQuality quality,
        VisualTraits visual) {

    public ComponentInfo {
        visual = visual == null ? VisualTraits.NONE : visual;
    }
}
