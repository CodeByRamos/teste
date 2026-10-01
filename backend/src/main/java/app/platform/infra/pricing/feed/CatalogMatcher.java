package app.platform.infra.pricing.feed;

import app.platform.catalog.Catalog;
import app.platform.pricing.Gtin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Finds which catalog component a store listing refers to, strictest evidence first. Ambiguity never guesses:
 * when a code points to more than one component, there is no match.
 *
 * <ol>
 *   <li>barcode (EAN/UPC/GTIN, check digit verified);</li>
 *   <li>manufacturer part number, unique in the catalog, with the same brand;</li>
 *   <li>a manufacturer part number found inside the listing title (stores such as KaBuM put it there instead of in
 *       structured data), with the same brand; codes shared by several components are ignored and every code
 *       that identifies a component must point to the same one.</li>
 * </ol>
 */
public final class CatalogMatcher {

    public enum Method { GTIN, MPN, MPN_IN_TITLE }

    public record Match(UUID componentId, Method method) {
    }

    private final Map<String, Set<UUID>> gtinIndex;
    private final Map<String, Set<UUID>> mpnIndex;
    private final Catalog catalog;

    public CatalogMatcher(Map<String, Set<UUID>> gtinIndex, Map<String, Set<UUID>> mpnIndex, Catalog catalog) {
        this.gtinIndex = gtinIndex;
        this.mpnIndex = mpnIndex;
        this.catalog = catalog;
    }

    public static CatalogMatcher load(StoreOfferRepository repository, Catalog catalog) {
        return new CatalogMatcher(repository.gtinIndex(), repository.mpnIndex(), catalog);
    }

    /** @return empty when nothing matches, the evidence is ambiguous, or the listing is clearly another kind of product */
    public Optional<Match> match(String gtin, String mpn, String brand, String title) {
        Optional<String> listedKind = kindInTitle(title);
        return findMatch(gtin, mpn, brand, title).filter(match -> {
            String kind = catalog.find(match.componentId()).map(component -> component.category().name()).orElse("");
            if (listedKind.isPresent()) {
                // Source data can be wrong (a cooler record carrying its bundled fans' part numbers); the store's own
                // words about what it is selling are a cheap, strong cross-check.
                return listedKind.get().equals(kind);
            }
            // The weakest evidence needs the title to confirm the kind of product.
            return match.method() != Method.MPN_IN_TITLE;
        });
    }

    private Optional<Match> findMatch(String gtin, String mpn, String brand, String title) {
        Optional<String> code = Gtin.normalize(gtin);
        if (code.isPresent()) {
            List<UUID> byCode = inCatalog(gtinIndex.get(code.get()));
            if (byCode.size() == 1) {
                return Optional.of(new Match(byCode.getFirst(), Method.GTIN));
            }
            if (byCode.size() > 1) {
                return Optional.empty();
            }
        }
        String structured = normalizeMpn(mpn);
        if (structured != null) {
            List<UUID> byMpn = withBrand(structured, brand);
            if (byMpn.size() == 1) {
                return Optional.of(new Match(byMpn.getFirst(), Method.MPN));
            }
        }
        return fromTitle(title, brand).map(id -> new Match(id, Method.MPN_IN_TITLE));
    }

    /**
     * Each title candidate is judged on its own, most specific first. A code that fits several components of the brand
     * ("B650M" names a chipset, not a board) says nothing and is skipped, as is a shorter code inside a longer one that
     * already identified the product. Every code that does identify a component must agree on the same one.
     */
    private Optional<UUID> fromTitle(String title, String brand) {
        List<String> candidates = titleCandidates(title).stream()
                .filter(candidate -> !GENERIC_TOKEN.matcher(candidate).matches())
                .sorted(java.util.Comparator.comparingInt(String::length).reversed())
                .toList();
        List<String> identifying = new java.util.ArrayList<>();
        Set<UUID> found = new HashSet<>();
        for (String candidate : candidates) {
            if (identifying.stream().anyMatch(longer -> longer.contains(candidate))) {
                continue;
            }
            List<UUID> ids = withBrand(candidate, brand);
            if (ids.size() == 1) {
                identifying.add(candidate);
                found.add(ids.getFirst());
            }
        }
        return found.size() == 1 ? Optional.of(found.iterator().next()) : Optional.empty();
    }

    /** Capacities, speeds and wattages ("120GB", "3200MHZ", "750W") repeat across products and never identify one. */
    private static final java.util.regex.Pattern GENERIC_TOKEN =
            java.util.regex.Pattern.compile("\\d+(?:[.,]\\d+)?(?:GB|TB|MB|MHZ|GHZ|HZ|W|RPM|MM|CM|V)|DDR\\d.*|PCIE.*|GEN\\d.*");

    /** Brands sold under more than one name: store listings use one, the catalog source another. */
    private static final List<Set<String>> BRAND_ALIASES = List.of(
            Set.of("westerndigital", "wd", "wdblack", "sandisk"),
            Set.of("adata", "xpg"),
            Set.of("teamgroup", "team", "tforce"),
            Set.of("gigabyte", "aorus"),
            Set.of("kingston", "kingstonfury", "hyperx"),
            Set.of("coolermaster", "cm"),
            Set.of("seasonic", "seasonicelectronics"));

    /** Product kinds as Brazilian stores name them at the start of a title; OTHER covers what the catalog does not carry. */
    private static final Map<String, String> KIND_WORDS = Map.ofEntries(
            Map.entry("placa de video", "GPU"), Map.entry("placa grafica", "GPU"),
            Map.entry("processador", "CPU"),
            Map.entry("placa mae", "MOTHERBOARD"), Map.entry("placa-mae", "MOTHERBOARD"), Map.entry("motherboard", "MOTHERBOARD"),
            Map.entry("memoria", "MEMORY"),
            Map.entry("ssd", "STORAGE"), Map.entry("hd", "STORAGE"), Map.entry("disco rigido", "STORAGE"),
            Map.entry("fonte", "POWER_SUPPLY"),
            Map.entry("gabinete", "CASE"),
            Map.entry("cooler", "CPU_COOLER"), Map.entry("water cooler", "CPU_COOLER"), Map.entry("watercooler", "CPU_COOLER"),
            Map.entry("air cooler", "CPU_COOLER"),
            Map.entry("ventoinha", "OTHER"), Map.entry("ventoinhas", "OTHER"), Map.entry("monitor", "OTHER"),
            Map.entry("teclado", "OTHER"), Map.entry("mouse", "OTHER"), Map.entry("headset", "OTHER"), Map.entry("cadeira", "OTHER"),
            Map.entry("notebook", "OTHER"), Map.entry("pasta termica", "OTHER"), Map.entry("cabo", "OTHER"),
            Map.entry("adaptador", "OTHER"), Map.entry("pc gamer", "OTHER"), Map.entry("computador", "OTHER"));

    /** The kind named earliest in the title ("Kit com 3 Ventoinhas ..." is OTHER; "Cooler Fan ..." is CPU_COOLER). */
    static Optional<String> kindInTitle(String title) {
        if (title == null) {
            return Optional.empty();
        }
        String folded = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String best = null;
        int bestAt = Integer.MAX_VALUE;
        int bestLength = 0;
        for (Map.Entry<String, String> entry : KIND_WORDS.entrySet()) {
            java.util.regex.Matcher found = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(entry.getKey()) + "\\b")
                    .matcher(folded);
            if (found.find() && (found.start() < bestAt || (found.start() == bestAt && entry.getKey().length() > bestLength))) {
                best = entry.getValue();
                bestAt = found.start();
                bestLength = entry.getKey().length();
            }
        }
        return Optional.ofNullable(best);
    }

    /** Title words that look like part numbers: at least 5 characters with a digit ("RM-MB-04-12V", "BX8071514600KF"). */
    public static Set<String> titleCandidates(String title) {
        Set<String> candidates = new HashSet<>();
        if (title == null) {
            return candidates;
        }
        for (String word : title.split("[\\s,;|()\\[\\]]+")) {
            String token = normalizeMpn(word.replaceAll("^[-/.:]+|[-/.:]+$", ""));
            if (token != null && token.chars().anyMatch(Character::isDigit)) {
                candidates.add(token);
            }
        }
        int dash = title.lastIndexOf(" - ");
        if (dash >= 0) {
            String tail = normalizeMpn(title.substring(dash + 3));
            if (tail != null) {
                candidates.add(tail);
            }
        }
        return candidates;
    }

    /** Components in the catalog carrying this part number and made by the listing's brand; none without a brand. */
    private List<UUID> withBrand(String mpn, String brand) {
        if (brand == null || brand.isBlank()) {
            return List.of();
        }
        String wanted = foldBrand(brand);
        return inCatalog(mpnIndex.get(mpn)).stream()
                .filter(id -> catalog.find(id)
                        .map(component -> component.info().manufacturer())
                        .map(CatalogMatcher::foldBrand)
                        .filter(maker -> sameBrand(maker, wanted))
                        .isPresent())
                .toList();
    }

    static boolean sameBrand(String maker, String wanted) {
        if (maker.isEmpty() || wanted.isEmpty()) {
            return false;
        }
        if (maker.contains(wanted) || wanted.contains(maker)) {
            return true;
        }
        return BRAND_ALIASES.stream().anyMatch(group -> group.contains(maker) && group.contains(wanted));
    }

    private List<UUID> inCatalog(Set<UUID> ids) {
        return ids == null ? List.of() : ids.stream().filter(id -> catalog.find(id).isPresent()).toList();
    }

    /** Uppercase without spaces; {@code null} when too short to identify a product. */
    public static String normalizeMpn(String raw) {
        if (raw == null) {
            return null;
        }
        String mpn = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return mpn.length() >= 5 ? mpn : null;
    }

    private static String foldBrand(String brand) {
        return brand.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
