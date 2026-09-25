package app.platform.catalog;

import app.platform.hardware.ComponentCategory;
import app.platform.hardware.HardwareComponent;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable, in-memory view of the active hardware catalog. The recommendation engine evaluates
 * thousands of combinations per request, so it works against memory rather than the database.
 */
public final class Catalog {

    private final Map<UUID, HardwareComponent> byId;
    private final Map<ComponentCategory, List<HardwareComponent>> byCategory;
    private final Map<UUID, String> searchText;
    private final CatalogVersion version;

    public Catalog(Collection<? extends HardwareComponent> components, CatalogVersion version) {
        this.byId = components.stream().collect(Collectors.toUnmodifiableMap(HardwareComponent::id, Function.identity()));
        Map<ComponentCategory, List<HardwareComponent>> grouped = new EnumMap<>(ComponentCategory.class);
        for (ComponentCategory category : ComponentCategory.values()) {
            grouped.put(category, components.stream()
                    .filter(component -> component.category() == category)
                    .map(HardwareComponent.class::cast)
                    .toList());
        }
        this.byCategory = Map.copyOf(grouped);
        this.searchText = components.stream().collect(Collectors.toUnmodifiableMap(HardwareComponent::id, c -> fold(c.name())));
        this.version = version;
    }

    public static Catalog empty() {
        return new Catalog(List.of(), CatalogVersion.NONE);
    }

    public Optional<HardwareComponent> find(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<HardwareComponent> all(ComponentCategory category) {
        return byCategory.get(category);
    }

    @SuppressWarnings("unchecked")
    public <T extends HardwareComponent> List<T> all(ComponentCategory category, Class<T> type) {
        return (List<T>) byCategory.get(category);
    }

    public int size() {
        return byId.size();
    }

    public CatalogVersion version() {
        return version;
    }

    /**
     * Accent- and case-insensitive search where every query term must appear in the name.
     * Results with better data quality and newer release come first.
     */
    public List<HardwareComponent> search(ComponentCategory category, String query, int limit) {
        String[] terms = Arrays.stream(fold(query == null ? "" : query).split("\\s+"))
                .filter(term -> !term.isBlank())
                .toArray(String[]::new);
        return (category == null ? byId.values().stream() : byCategory.get(category).stream())
                .filter(component -> {
                    String text = searchText.get(component.id());
                    return Arrays.stream(terms).allMatch(text::contains);
                })
                .sorted(Comparator
                        .comparingDouble((HardwareComponent c) -> -c.info().quality().score())
                        .thenComparing(c -> -(c.info().releaseYear() == null ? 0 : c.info().releaseYear()))
                        .thenComparing(HardwareComponent::name))
                .limit(limit)
                .toList();
    }

    private static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
