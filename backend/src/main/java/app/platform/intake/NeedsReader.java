package app.platform.intake;

import java.util.Optional;

/** Reads a free-text request with a language model. Empty when unavailable, too slow, refused or failed. */
@FunctionalInterface
public interface NeedsReader {

    NeedsReader NONE = text -> Optional.empty();

    Optional<ModelReading> read(String text);
}
