package app.platform.infra.pricing.feed;

import app.platform.Fixtures;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.Gpu;
import app.platform.pricing.Gtin;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMatcherTest {

    private final Gpu gpu = Fixtures.one(Gpu.class);
    private final CpuCooler cooler = Fixtures.one(CpuCooler.class);

    /**
     * The GPU carries its real barcode; the cooler record also lists a fan kit's part number, as happened with a
     * real OpenDB water cooler that listed its bundled fans.
     */
    private final CatalogMatcher matcher = new CatalogMatcher(
            Map.of(Gtin.normalize("0824142335499").orElseThrow(), Set.of(gpu.id())),
            Map.of("PA140SE-BLACK", Set.of(cooler.id()), "MPG-EZ120-ARGB-WHITE-3W", Set.of(cooler.id())),
            Fixtures.catalog());

    @Test
    void readsTheKindOfProductTheStoreIsSelling() {
        assertThat(CatalogMatcher.kindInTitle("Kit com 3 Ventoinhas MSI MPG EZ120 ARGB")).contains("OTHER");
        assertThat(CatalogMatcher.kindInTitle("Cooler Fan Thermaltake TT UX200 ARGB")).contains("CPU_COOLER");
        assertThat(CatalogMatcher.kindInTitle("Placa de Vídeo MSI RTX 4070 Ventus")).contains("GPU");
        assertThat(CatalogMatcher.kindInTitle("Placa-Mãe ASUS Prime B650M")).contains("MOTHERBOARD");
        assertThat(CatalogMatcher.kindInTitle("Water Cooler MSI MAG Coreliquid")).contains("CPU_COOLER");
        assertThat(CatalogMatcher.kindInTitle("Produto sem tipo")).isEmpty();
        assertThat(CatalogMatcher.kindInTitle(null)).isEmpty();
    }

    @Test
    void aListingOfAnotherKindIsNotMatchedEvenWhenAPartNumberFits() {
        // Real case: the fan kit's part number sits on a water cooler record in the source data.
        assertThat(matcher.match(null, null, cooler.info().manufacturer(),
                "Kit com 3 Ventoinhas MSI MPG EZ120 ARGB 120mm - MPG-EZ120-ARGB-WHITE-3W")).isEmpty();
        // Even a barcode match is refused when the store says it sells something else.
        assertThat(matcher.match("0824142335499", null, "MSI", "Ventoinha MSI 120mm")).isEmpty();
    }

    @Test
    void matchingListingsStillMatch() {
        assertThat(matcher.match("0824142335499", null, "MSI", "Placa de Vídeo MSI GeForce RTX 4070"))
                .hasValueSatisfying(match -> assertThat(match.componentId()).isEqualTo(gpu.id()));
        assertThat(matcher.match("0824142335499", null, null, null))
                .hasValueSatisfying(match -> assertThat(match.method()).isEqualTo(CatalogMatcher.Method.GTIN));
        assertThat(matcher.match(null, null, cooler.info().manufacturer(), "Cooler para Processador Thermalright - PA140SE-BLACK"))
                .hasValueSatisfying(match -> assertThat(match.method()).isEqualTo(CatalogMatcher.Method.MPN_IN_TITLE));
        // Part number only in the title, and the title does not say what it is: not enough evidence.
        assertThat(matcher.match(null, null, cooler.info().manufacturer(), "Thermalright PA140SE-BLACK")).isEmpty();
    }
}
