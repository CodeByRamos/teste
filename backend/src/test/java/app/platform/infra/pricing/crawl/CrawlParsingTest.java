package app.platform.infra.pricing.crawl;

import app.platform.infra.pricing.feed.CatalogMatcher;
import app.platform.pricing.Offer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlParsingTest {

    private static final String KABUM_LIKE_ROBOTS = """
            User-agent: *
            Allow: /.css
            Disallow: /carrinho*
            Disallow: /busca/*?
            Disallow: *sort=price
            Disallow: /*?awc=*
            """;

    @Test
    void robotsRulesFollowTheStandard() {
        RobotsRules rules = RobotsRules.parse(KABUM_LIKE_ROBOTS, "PCPriceBot");

        assertThat(rules.allows("/produto/153638/ventoinha-rise-mode")).isTrue();
        assertThat(rules.allows("/carrinho/finalizar")).isFalse();
        assertThat(rules.allows("/busca/placa?x=1")).isFalse();
        assertThat(rules.allows("/hardware?sort=price")).isFalse();
        assertThat(rules.allows("/produto/1?awc=abc")).isFalse();
        assertThat(rules.crawlDelay()).isEmpty();
    }

    @Test
    void ourOwnGroupWinsOverTheGenericOneAndLongestRuleWins() {
        RobotsRules rules = RobotsRules.parse("""
                User-agent: *
                Disallow: /

                User-agent: PCPriceBot
                Disallow: /produto/
                Allow: /produto/ok/
                Crawl-delay: 10
                """, "PCPriceBot");

        assertThat(rules.allows("/produto/ok/1")).isTrue();
        assertThat(rules.allows("/produto/outro")).isFalse();
        assertThat(rules.allows("/categoria")).isTrue(); // the "*" group does not apply to us once we have our own
        assertThat(rules.crawlDelay()).contains(Duration.ofSeconds(10));
        assertThat(RobotsRules.denyAll().allows("/anything")).isFalse();
        assertThat(RobotsRules.allowAll().allows("/anything")).isTrue();
        assertThat(RobotsRules.parse("User-agent: *\nDisallow: /*.pdf$", "x").allows("/a.pdf")).isFalse();
        assertThat(RobotsRules.parse("User-agent: *\nDisallow: /*.pdf$", "x").allows("/a.pdf?x")).isTrue();
    }

    @Test
    void sitemapsAreReadAndExternalEntitiesAreNotResolved() throws Exception {
        String index = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <sitemap><loc>https://www.loja.example/sitemap/hardware.xml</loc></sitemap>
                  <sitemap><loc>https://www.loja.example/sitemap/moveis.xml</loc></sitemap>
                </sitemapindex>""";
        SitemapParser.Sitemap parsed = SitemapParser.parse(new ByteArrayInputStream(index.getBytes(StandardCharsets.UTF_8)), 100);
        assertThat(parsed.childSitemaps()).hasSize(2);
        assertThat(parsed.pages()).isEmpty();

        String hostile = """
                <?xml version="1.0"?>
                <!DOCTYPE urlset [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <urlset><url><loc>https://www.loja.example/produto/&secret;</loc></url></urlset>""";
        assertThatThrownBy(() -> SitemapParser.parse(new ByteArrayInputStream(hostile.getBytes(StandardCharsets.UTF_8)), 100))
                .isInstanceOf(Exception.class);
    }

    @Test
    void readsTerabyteStyleJsonLdWithBarcodeAndPartNumber() {
        String html = """
                <html><head>
                <script type="application/ld+json">{"@context":"https://schema.org","@type":"BreadcrumbList"}</script>
                <script type="application/ld+json">
                {"@context":"https://schema.org/","@type":"Product","name":"SSD Kingston A400 240GB | Loja",
                 "brand":{"@type":"Thing","name":"Kingston"},"gtin13":"740617261219","mpn":"SA400S37/240G",
                 "offers":{"@type":"Offer","priceCurrency":"BRL","price":"429.90","availability":"http://schema.org/InStock"}}
                </script></head></html>""";

        ProductPageParser.PageProduct product = ProductPageParser.parse(html).orElseThrow();

        assertThat(product.brand()).isEqualTo("Kingston");
        assertThat(product.gtin()).isEqualTo("740617261219");
        assertThat(product.mpn()).isEqualTo("SA400S37/240G");
        assertThat(product.price()).isEqualByComparingTo("429.90");
        assertThat(product.currency()).isEqualTo("BRL");
        assertThat(product.availability()).isEqualTo(Offer.Availability.IN_STOCK);
    }

    @Test
    void readsKabumStyleJsonLdAndSurvivesBrokenBlocks() {
        String html = """
                <script type="application/ld+json">{ this is not json </script>
                <script type='application/ld+json'>{"@context":"https://schema.org","@graph":[
                  {"@type":"WebPage"},
                  {"@type":"Product","name":"Ventoinha Rise Mode Galaxy 140mm, RGB, Preto - RM-MB-04-12V",
                   "brand":{"@type":"Brand","name":"Rise Mode"},"sku":"153638",
                   "offers":[{"@type":"Offer","priceCurrency":"BRL","price":43.99,"availability":"https://schema.org/OutOfStock"}]}]}
                </script>""";

        ProductPageParser.PageProduct product = ProductPageParser.parse(html).orElseThrow();

        assertThat(product.gtin()).isNull();
        assertThat(product.mpn()).isNull();
        assertThat(product.price()).isEqualByComparingTo("43.99");
        assertThat(product.availability()).isEqualTo(Offer.Availability.OUT_OF_STOCK);
        assertThat(CatalogMatcher.titleCandidates(product.name())).contains("RM-MB-04-12V");
    }

    @Test
    void pagesWithoutProductOrPriceGiveNothing() {
        assertThat(ProductPageParser.parse("<html>sem dados</html>")).isEmpty();
        assertThat(ProductPageParser.parse("""
                <script type="application/ld+json">{"@type":"Product","name":"x","offers":{"price":"sob consulta"}}</script>""")).isEmpty();
    }
}
