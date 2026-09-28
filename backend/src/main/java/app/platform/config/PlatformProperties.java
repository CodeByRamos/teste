package app.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

/**
 * @param opendb    where the pinned OpenDB snapshot lives and whether to ingest it at startup
 * @param pricing   which price providers are active
 * @param rateLimit per-client request budget for expensive endpoints
 * @param frontend  how the web frontend's server proves requests are relayed by it
 * @param admin     operator-only endpoints (price feed uploads)
 */
@ConfigurationProperties("platform")
public record PlatformProperties(OpenDb opendb, Pricing pricing, RateLimit rateLimit, Frontend frontend, Admin admin) {

    public PlatformProperties {
        opendb = opendb == null ? new OpenDb(null, false) : opendb;
        pricing = pricing == null ? new Pricing(false, null, null, null) : pricing;
        rateLimit = rateLimit == null ? new RateLimit(60, List.of()) : rateLimit;
        frontend = frontend == null ? new Frontend(null) : frontend;
        admin = admin == null ? new Admin(null) : admin;
    }

    /** @param snapshotDir directory produced by scripts/fetch-opendb.sh; may be null in production if ingestion runs as a job */
    public record OpenDb(Path snapshotDir, boolean ingestOnStartup) {
    }

    /**
     * @param examplePrices enables fictitious development prices; real store offers take precedence where they exist
     * @param feeds         store product feeds (affiliate networks) downloaded periodically
     * @param feedRefresh   how often feeds are downloaded again
     * @param maxOfferAge   offers older than this are not shown
     */
    public record Pricing(boolean examplePrices, List<Feed> feeds, java.time.Duration feedRefresh, java.time.Duration maxOfferAge) {
        public Pricing {
            feeds = feeds == null ? List.of() : List.copyOf(feeds);
            feedRefresh = feedRefresh == null ? java.time.Duration.ofHours(6) : feedRefresh;
            maxOfferAge = maxOfferAge == null ? java.time.Duration.ofHours(72) : maxOfferAge;
        }
    }

    /**
     * One store's feed. Set through environment variables, e.g. {@code PLATFORM_PRICING_FEEDS_0_URL}; the URL usually
     * contains the network's API key and is treated as a secret.
     *
     * @param id             stable identifier, lowercase (e.g. "kabum")
     * @param store          name shown to people (e.g. "KaBuM!")
     * @param format         "generic" or "awin"
     * @param url            HTTPS download URL; empty when the feed is only uploaded through the admin endpoint
     * @param allowedDomains hosts the offer links may point to (store and affiliate redirect hosts)
     * @param columns        column name overrides (keys: gtin, price, url, availability, observed-at, currency)
     * @param delimiter      field separator, default comma
     */
    public record Feed(String id, String store, String format, java.net.URI url, List<String> allowedDomains,
                       java.util.Map<String, String> columns, String delimiter) {
        public Feed {
            allowedDomains = allowedDomains == null ? List.of() : List.copyOf(allowedDomains);
            columns = columns == null ? java.util.Map.of() : java.util.Map.copyOf(columns);
            delimiter = delimiter == null || delimiter.isEmpty() ? "," : delimiter;
        }
    }

    /** @param token required in X-Admin-Token for operator endpoints; unset disables them entirely */
    public record Admin(String token) {
        public Admin {
            token = token == null || token.isBlank() ? null : token;
        }
    }

    /**
     * @param trustedProxies addresses allowed to report the real client address in X-Forwarded-For
     *                       (e.g. the web frontend server); requests from anywhere else are keyed by socket address
     */
    public record RateLimit(int requestsPerMinute, List<String> trustedProxies) {

        public RateLimit {
            trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        }
    }

    /**
     * @param sharedSecret when set, {@code /api/**} only answers requests carrying it (sent by the frontend's server,
     *                     never by browsers), and the client address those requests report is trusted for rate limiting.
     *                     Unset in local development.
     */
    public record Frontend(String sharedSecret) {
        public Frontend {
            sharedSecret = sharedSecret == null || sharedSecret.isBlank() ? null : sharedSecret;
        }
    }
}
