package app.platform.infra.pricing.feed;

import app.platform.pricing.Offer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FeedParsingTest {

    @Test
    void pricesInBrazilianAndInternationalNotation() {
        assertThat(FeedValues.price("3899.90")).isEqualByComparingTo("3899.90");
        assertThat(FeedValues.price("3899,90")).isEqualByComparingTo("3899.90");
        assertThat(FeedValues.price("R$ 3.899,90")).isEqualByComparingTo("3899.90");
        assertThat(FeedValues.price("3,899.90")).isEqualByComparingTo("3899.90");
        assertThat(FeedValues.price("1.234.567")).isEqualByComparingTo("1234567");
        assertThat(FeedValues.price("450")).isEqualByComparingTo(new BigDecimal("450"));
    }

    @Test
    void ambiguousOrInvalidPricesAreNotGuessed() {
        assertThat(FeedValues.price("1.234")).isNull();
        assertThat(FeedValues.price("1,234")).isNull();
        assertThat(FeedValues.price("abc")).isNull();
        assertThat(FeedValues.price("")).isNull();
    }

    @Test
    void availabilityAndDates() {
        assertThat(FeedValues.availability("1")).isEqualTo(Offer.Availability.IN_STOCK);
        assertThat(FeedValues.availability("Esgotado")).isEqualTo(Offer.Availability.OUT_OF_STOCK);
        assertThat(FeedValues.availability("talvez")).isEqualTo(Offer.Availability.UNKNOWN);
        assertThat(FeedValues.instant("2026-09-28T12:00:00Z")).isEqualTo(Instant.parse("2026-09-28T12:00:00Z"));
        // Local store time (Brasília, UTC-3).
        assertThat(FeedValues.instant("2026-09-28 09:00:00")).isEqualTo(Instant.parse("2026-09-28T12:00:00Z"));
        assertThat(FeedValues.instant("ontem")).isNull();
    }

    @Test
    void csvHandlesQuotesDelimitersAndLineBreaksInsideFields() throws Exception {
        String content = "a,b,c\r\n"
                + "\"x, y\",\"say \"\"hi\"\"\",\"line1\nline2\"\n"
                + "\n"
                + "last,,\n";
        CsvReader csv = new CsvReader(new StringReader(content), ',');

        assertThat(csv.next()).containsExactly("a", "b", "c");
        assertThat(csv.next()).containsExactly("x, y", "say \"hi\"", "line1\nline2");
        assertThat(csv.next()).containsExactly("last", "", "");
        assertThat(csv.next()).isNull();
    }

    @Test
    void gzipIsDetectedFromContent() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write("gtin,price\n".getBytes(StandardCharsets.UTF_8));
        }
        byte[] unzipped = FeedStreams.decode(new ByteArrayInputStream(bytes.toByteArray())).readAllBytes();
        assertThat(new String(unzipped, StandardCharsets.UTF_8)).isEqualTo("gtin,price\n");
        byte[] plain = FeedStreams.decode(new ByteArrayInputStream("plain".getBytes(StandardCharsets.UTF_8))).readAllBytes();
        assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo("plain");
    }

    @Test
    void feedColumnsCanBeOverridden() {
        assertThat(FeedFormat.AWIN.columns(Map.of("gtin", "product_GTIN")).get(FeedFormat.Column.GTIN)).isEqualTo("product_GTIN");
        assertThat(FeedFormat.of("awin")).isEqualTo(FeedFormat.AWIN);
        assertThat(FeedFormat.of(null)).isEqualTo(FeedFormat.GENERIC);
        assertThat(FeedFormat.GENERIC.columns(Map.of("observedat", "seen")).get(FeedFormat.Column.OBSERVED_AT)).isEqualTo("seen");
        assertThat(FeedFormat.GENERIC.columns(Map.of("observed-at", "seen")).get(FeedFormat.Column.OBSERVED_AT)).isEqualTo("seen");
    }
}
