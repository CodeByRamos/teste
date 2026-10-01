package app.platform.infra.pricing.crawl;

import app.platform.pricing.Offer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the schema.org Product/Offer data a store embeds in its product page (JSON-LD, the format stores publish for
 * search engines). Only structured data is used: no guessing from the visual layout, so a redesign does not break
 * reading and nothing is inferred that the store did not state.
 */
public final class ProductPageParser {

    /**
     * What a product page states.
     *
     * @param gtin  barcode, when the page gives one
     * @param mpn   manufacturer part number, when the page gives one
     * @param price lowest offered price in BRL
     */
    public record PageProduct(String name, String brand, String gtin, String mpn, BigDecimal price, String currency,
                              Offer.Availability availability) {
    }

    private static final Pattern JSON_LD = Pattern.compile(
            "<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ProductPageParser() {
    }

    public static Optional<PageProduct> parse(String html) {
        Matcher matcher = JSON_LD.matcher(html);
        while (matcher.find()) {
            JsonNode root;
            try {
                root = JSON.readTree(matcher.group(1).strip());
            } catch (RuntimeException e) {
                continue; // one malformed block does not hide a valid one
            }
            for (JsonNode node : candidates(root)) {
                if (isType(node, "Product")) {
                    Optional<PageProduct> product = product(node);
                    if (product.isPresent()) {
                        return product;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static List<JsonNode> candidates(JsonNode root) {
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else if (root.isObject()) {
            nodes.add(root);
            JsonNode graph = root.path("@graph");
            if (graph.isArray()) {
                graph.forEach(nodes::add);
            }
        }
        return nodes;
    }

    private static boolean isType(JsonNode node, String type) {
        JsonNode value = node.path("@type");
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (type.equalsIgnoreCase(item.asString(""))) {
                    return true;
                }
            }
            return false;
        }
        return type.equalsIgnoreCase(value.asString(""));
    }

    private static Optional<PageProduct> product(JsonNode node) {
        JsonNode offer = firstOffer(node.path("offers"));
        if (offer == null) {
            return Optional.empty();
        }
        BigDecimal price = decimal(offer.has("price") ? offer.path("price") : offer.path("lowPrice"));
        if (price == null) {
            return Optional.empty();
        }
        String currency = text(offer.path("priceCurrency"));
        return Optional.of(new PageProduct(
                text(node.path("name")),
                brand(node.path("brand")),
                firstText(node, "gtin13", "gtin", "gtin14", "gtin12", "gtin8"),
                text(node.path("mpn")),
                price,
                currency,
                availability(text(offer.path("availability")))));
    }

    private static JsonNode firstOffer(JsonNode offers) {
        if (offers.isArray()) {
            for (JsonNode item : offers) {
                if (item.isObject()) {
                    return item;
                }
            }
            return null;
        }
        if (offers.isObject()) {
            JsonNode nested = offers.path("offers");
            return !offers.has("price") && !offers.has("lowPrice") && (nested.isArray() || nested.isObject())
                    ? firstOffer(nested)
                    : offers;
        }
        return null;
    }

    private static String brand(JsonNode brand) {
        return brand.isObject() ? text(brand.path("name")) : text(brand);
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node.path(field));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.isString() ? node.stringValue() : node.isNumber() ? node.asString() : null;
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Numbers or plain numeric strings ("429.90"); anything else is not a price. */
    private static BigDecimal decimal(JsonNode node) {
        try {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (node.isString() && node.stringValue().strip().matches("\\d+(\\.\\d{1,2})?")) {
                return new BigDecimal(node.stringValue().strip());
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private static Offer.Availability availability(String value) {
        if (value == null) {
            return Offer.Availability.UNKNOWN;
        }
        String folded = value.toLowerCase(Locale.ROOT);
        if (folded.endsWith("instock") || folded.endsWith("limitedavailability") || folded.endsWith("onlineonly")) {
            return Offer.Availability.IN_STOCK;
        }
        if (folded.endsWith("outofstock") || folded.endsWith("soldout") || folded.endsWith("discontinued")) {
            return Offer.Availability.OUT_OF_STOCK;
        }
        return Offer.Availability.UNKNOWN;
    }
}
