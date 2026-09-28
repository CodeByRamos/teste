package app.platform.pricing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GtinTest {

    @Test
    void upcAndItsEanFormCompareEqual() {
        assertThat(Gtin.normalize("824142335499")).isEqualTo(Gtin.normalize("0824142335499"));
        assertThat(Gtin.normalize("0824142335499")).contains("00824142335499");
    }

    @Test
    void rejectsWrongCheckDigitsAndNonCodes() {
        assertThat(Gtin.normalize("0824142335498")).isEmpty();
        assertThat(Gtin.normalize("12345")).isEmpty();
        assertThat(Gtin.normalize("ABC4142335499")).isEmpty();
        assertThat(Gtin.normalize("0000000000000")).isEmpty();
        assertThat(Gtin.normalize(null)).isEmpty();
    }
}
