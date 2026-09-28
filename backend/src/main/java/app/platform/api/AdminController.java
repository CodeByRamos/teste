package app.platform.api;

import app.platform.config.PlatformProperties;
import app.platform.config.PriceFeeds;
import app.platform.infra.pricing.feed.FeedImporter;
import app.platform.infra.pricing.feed.FeedStreams;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Operator endpoints. Disabled (404) unless ADMIN_TOKEN is set; outside /api so the site never exposes them.
 *
 * <pre>curl -X POST -H "X-Admin-Token: …" --data-binary @feed.csv.gz https://api…/admin/price-feeds/kabum</pre>
 */
@RestController
@RequestMapping("/admin")
class AdminController {

    private final byte[] token;
    private final PriceFeeds feeds;

    AdminController(PlatformProperties properties, PriceFeeds feeds) {
        String configured = properties.admin().token();
        this.token = configured == null ? null : configured.getBytes(StandardCharsets.UTF_8);
        this.feeds = feeds;
    }

    /** Imports an uploaded feed (CSV, optionally gzipped) for a configured store. */
    @PostMapping("/price-feeds/{feedId}")
    ResponseEntity<FeedImporter.Summary> importFeed(@PathVariable String feedId,
                                                    @RequestHeader(value = "X-Admin-Token", required = false) String presented,
                                                    HttpServletRequest request) throws IOException {
        if (token == null) {
            return ResponseEntity.notFound().build();
        }
        if (presented == null || !MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(403).build();
        }
        var feed = feeds.feed(feedId);
        if (feed.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try (InputStream body = FeedStreams.decode(request.getInputStream())) {
            return ResponseEntity.ok(feeds.importer().importFeed(feed.get(), body));
        }
    }
}
