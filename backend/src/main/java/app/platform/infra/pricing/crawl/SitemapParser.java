package app.platform.infra.pricing.crawl;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads sitemap files (sitemaps.org): either an index pointing to more sitemaps, or a list of page URLs. Streaming,
 * with DTDs and external entities disabled: the XML comes from third-party sites (no XXE).
 */
public final class SitemapParser {

    /** @param childSitemaps set for a sitemap index; @param pages set for a URL set */
    public record Sitemap(List<String> childSitemaps, List<String> pages) {
    }

    private static final XMLInputFactory FACTORY = createFactory();

    private SitemapParser() {
    }

    private static XMLInputFactory createFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        return factory;
    }

    /** @param maxUrls stops reading after this many locations (guards against huge or hostile files) */
    public static Sitemap parse(InputStream input, int maxUrls) throws XMLStreamException {
        List<String> children = new ArrayList<>();
        List<String> pages = new ArrayList<>();
        XMLStreamReader reader = FACTORY.createXMLStreamReader(input);
        try {
            String parent = null;
            while (reader.hasNext() && children.size() + pages.size() < maxUrls) {
                int event = reader.next();
                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String name = localName(reader.getLocalName());
                if (name.equals("sitemap") || name.equals("url")) {
                    parent = name;
                } else if (name.equals("loc") && parent != null) {
                    String location = reader.getElementText().strip();
                    if (!location.isEmpty()) {
                        (parent.equals("sitemap") ? children : pages).add(location);
                    }
                }
            }
        } finally {
            reader.close();
        }
        return new Sitemap(children, pages);
    }

    private static String localName(String name) {
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }
}
