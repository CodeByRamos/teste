package app.platform.infra.pricing.crawl;

import java.io.IOException;
import java.net.URI;

/** One polite HTTP GET. Redirects are returned, not followed, so the bot never leaves the store's allowed domains. */
public interface PageFetcher {

    /**
     * @param body     decoded page (gzip handled); empty for 304 and errors
     * @param location redirect target for 3xx, else {@code null}
     */
    record Response(int status, byte[] body, String etag, String lastModified, String location) {
        public boolean notModified() {
            return status == 304;
        }
    }

    Response get(URI uri, String etag, String lastModified) throws IOException;
}
