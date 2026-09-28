package app.platform.infra.pricing.feed;

import app.platform.pricing.Offer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Parses feed cell values. Anything ambiguous returns {@code null}: the row is dropped, never guessed. */
final class FeedValues {

    /** Feeds without an offset are Brazilian stores' local time. */
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm[:ss]");

    private FeedValues() {
    }

    /**
     * "1234.56", "1234,56", "1.234,56", "1,234.56", "R$ 1.234,56". A single dot or comma followed by exactly three
     * digits ("1.234") could be a thousands separator or a decimal point, so it is rejected.
     */
    static BigDecimal price(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.replace("R$", "").replace(' ', ' ').strip().replace(" ", "");
        if (value.isEmpty() || !value.matches("[0-9.,]+")) {
            return null;
        }
        int lastDot = value.lastIndexOf('.');
        int lastComma = value.lastIndexOf(',');
        String normalized;
        if (lastDot >= 0 && lastComma >= 0) {
            char decimal = lastDot > lastComma ? '.' : ',';
            char thousands = decimal == '.' ? ',' : '.';
            normalized = value.replace(String.valueOf(thousands), "").replace(decimal, '.');
        } else if (lastDot >= 0 || lastComma >= 0) {
            char separator = lastDot >= 0 ? '.' : ',';
            int count = (int) value.chars().filter(c -> c == separator).count();
            int decimals = value.length() - value.lastIndexOf(separator) - 1;
            if (count > 1) {
                normalized = value.replace(String.valueOf(separator), "");
            } else if (decimals == 3) {
                return null;
            } else {
                normalized = value.replace(separator, '.');
            }
        } else {
            normalized = value;
        }
        try {
            return new BigDecimal(normalized).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Offer.Availability availability(String raw) {
        if (raw == null || raw.isBlank()) {
            return Offer.Availability.UNKNOWN;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "1", "yes", "y", "true", "in stock", "instock", "in_stock", "em estoque", "disponivel", "disponível" ->
                    Offer.Availability.IN_STOCK;
            case "0", "no", "n", "false", "out of stock", "outofstock", "out_of_stock", "sem estoque", "indisponivel",
                 "indisponível", "esgotado" -> Offer.Availability.OUT_OF_STOCK;
            default -> Offer.Availability.UNKNOWN;
        };
    }

    /** ISO instant, ISO with offset, or local "yyyy-MM-dd HH:mm[:ss]" in Brazilian time; {@code null} otherwise. */
    static Instant instant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // try the next layout
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // try the next layout
        }
        try {
            return LocalDateTime.parse(value, LOCAL).atZone(STORE_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
