package app.platform.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OfferValidationTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private final OfferValidation validation = new OfferValidation(Set.of("kabum.com.br", "awin1.com"), Duration.ofHours(72));

    private OfferValidation.Rejection check(String price, String url, Instant observed, List<String> others, String previous) {
        return validation.check(new BigDecimal(price), url, observed, NOW, others.stream().map(BigDecimal::new).toList(),
                previous == null ? null : new BigDecimal(previous)).orElse(null);
    }

    @Test
    void acceptsAPlausibleOfferFromAnAllowedHost() {
        assertThat(check("3899.90", "https://www.kabum.com.br/produto/1", NOW.minusSeconds(3600), List.of(), null)).isNull();
        assertThat(check("3899.90", "https://www.awin1.com/cread.php?awinmid=1", NOW, List.of(), null)).isNull();
    }

    @Test
    void linksMustBeHttpsToAnAllowedHost() {
        assertThat(check("100", "http://www.kabum.com.br/p", NOW, List.of(), null)).isEqualTo(OfferValidation.Rejection.INVALID_URL);
        assertThat(check("100", "https://kabum.com.br.evil.example/p", NOW, List.of(), null))
                .isEqualTo(OfferValidation.Rejection.DOMAIN_NOT_ALLOWED);
        assertThat(check("100", "https://notkabum.com.br/p", NOW, List.of(), null)).isEqualTo(OfferValidation.Rejection.DOMAIN_NOT_ALLOWED);
        // "user@host" tricks: the real host is after the @.
        assertThat(check("100", "https://kabum.com.br@evil.example/p", NOW, List.of(), null)).isEqualTo(OfferValidation.Rejection.INVALID_URL);
        assertThat(check("100", "javascript:alert(1)", NOW, List.of(), null)).isEqualTo(OfferValidation.Rejection.INVALID_URL);
    }

    @Test
    void implausiblePricesAndDatesAreDropped() {
        assertThat(check("1.00", "https://kabum.com.br/p", NOW, List.of(), null)).isEqualTo(OfferValidation.Rejection.PRICE_OUT_OF_RANGE);
        assertThat(check("100", "https://kabum.com.br/p", NOW.plus(Duration.ofHours(2)), List.of(), null))
                .isEqualTo(OfferValidation.Rejection.FROM_THE_FUTURE);
        assertThat(check("100", "https://kabum.com.br/p", NOW.minus(Duration.ofDays(4)), List.of(), null))
                .isEqualTo(OfferValidation.Rejection.STALE);
    }

    @Test
    void manipulatedLookingPricesAreHeldBack() {
        // Far below what two other stores charge.
        assertThat(check("900", "https://kabum.com.br/p", NOW, List.of("3800", "4000"), null)).isEqualTo(OfferValidation.Rejection.OUTLIER);
        // One other store is not enough to call it an outlier.
        assertThat(check("900", "https://kabum.com.br/p", NOW, List.of("3800"), null)).isNull();
        // A sudden 70% drop from this store's own previous price.
        assertThat(check("1100", "https://kabum.com.br/p", NOW, List.of(), "3800")).isEqualTo(OfferValidation.Rejection.SUSPICIOUS_DROP);
    }
}
