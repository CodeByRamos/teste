package app.platform.hardware;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a component in the external source it came from, e.g. {@code buildcores-opendb / <opendb_id>}.
 * External identifiers are preserved verbatim so records can be traced, re-synced and audited.
 */
public record SourceRef(String source, String externalId) {

    public SourceRef {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(externalId, "externalId");
    }

    /**
     * Deterministic internal id: the same external record always maps to the same UUID,
     * so saved builds keep pointing at the right component across re-ingestions.
     */
    public UUID internalId() {
        return UUID.nameUUIDFromBytes((source + ":" + externalId).getBytes(StandardCharsets.UTF_8));
    }
}
