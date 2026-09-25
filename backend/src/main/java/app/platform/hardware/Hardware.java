package app.platform.hardware;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared parsing and normalization rules for hardware vocabulary. */
public final class Hardware {

    private static final Pattern GENERATION = Pattern.compile("(\\d+)(?:\\.\\d+)?");
    private static final Pattern PCIE_INTERFACE = Pattern.compile("PCIe(?:\\s+(\\d+)(?:\\.\\d+)?)?\\s+x(\\d+)");

    /**
     * Socket names that differ between categories in the source data but mean the same physical socket.
     * Keys are compared case-insensitively.
     */
    private static final Map<String, String> SOCKET_ALIASES = Map.of(
            "str4", "TR4",
            "lga 1151v2", "LGA 1151",
            "lga1151", "LGA 1151",
            "lga1700", "LGA 1700",
            "lga1851", "LGA 1851");

    private Hardware() {
    }

    /**
     * Immutable, alphabetically ordered copy. Ordering matters: these sets are serialized, and a stable order keeps
     * stored documents identical between runs (so re-ingestion can tell real changes apart).
     */
    public static Set<String> sortedSet(Collection<String> values) {
        return values == null ? Set.of() : Collections.unmodifiableSortedSet(new TreeSet<>(values));
    }

    /** Canonical socket name, or the trimmed input when no alias applies. */
    public static String normalizeSocket(String socket) {
        if (socket == null) {
            return null;
        }
        String trimmed = socket.trim().replaceAll("\\s+", " ");
        return SOCKET_ALIASES.getOrDefault(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }

    /** "4.0" → 4. Returns {@code null} when there is no number. */
    public static Integer parseGeneration(String generation) {
        if (generation == null) {
            return null;
        }
        Matcher matcher = GENERATION.matcher(generation);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** Parses strings like "PCIe 4.0 x16" or "PCIe x8" into generation (nullable) and lane count. */
    public static PcieLink parsePcieInterface(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = PCIE_INTERFACE.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        Integer generation = matcher.group(1) == null ? null : Integer.valueOf(matcher.group(1));
        return new PcieLink(generation, Integer.parseInt(matcher.group(2)));
    }

    public record PcieLink(Integer generation, int lanes) {
    }
}
