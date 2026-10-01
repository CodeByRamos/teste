package app.platform.infra.pricing.crawl;

import app.platform.infra.pricing.feed.FeedStreams;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The bot's HTTP client: identifies itself with a product token and a contact, sends conditional requests so
 * unchanged pages are not downloaded again, caps page size and never follows redirects on its own.
 */
public final class HttpPageFetcher implements PageFetcher {

    static final int MAX_PAGE_BYTES = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final String userAgent;

    /** @param userAgent e.g. "PCPriceBot/1.0 (+mailto:contato@exemplo.com.br)" */
    public HttpPageFetcher(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public Response get(URI uri, String etag, String lastModified) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Only HTTPS pages are fetched");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.5")
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .GET();
        if (etag != null) {
            request.header("If-None-Match", etag);
        }
        if (lastModified != null) {
            request.header("If-Modified-Since", lastModified);
        }
        HttpResponse<InputStream> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
        byte[] body;
        try (InputStream raw = response.body()) {
            body = response.statusCode() == 200 ? readLimited(FeedStreams.decode(raw)) : new byte[0];
        }
        return new Response(response.statusCode(), body,
                response.headers().firstValue("ETag").orElse(null),
                response.headers().firstValue("Last-Modified").orElse(null),
                response.headers().firstValue("Location").orElse(null));
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_PAGE_BYTES + 1);
        if (bytes.length > MAX_PAGE_BYTES) {
            throw new IOException("Page larger than " + MAX_PAGE_BYTES + " bytes");
        }
        return bytes;
    }
}
