package app.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

/**
 * @param opendb    where the pinned OpenDB snapshot lives and whether to ingest it at startup
 * @param pricing   which price providers are active
 * @param rateLimit per-client request budget for expensive endpoints
 * @param frontend  how the web frontend's server proves requests are relayed by it
 */
@ConfigurationProperties("platform")
public record PlatformProperties(OpenDb opendb, Pricing pricing, RateLimit rateLimit, Frontend frontend) {

    public PlatformProperties {
        opendb = opendb == null ? new OpenDb(null, false) : opendb;
        pricing = pricing == null ? new Pricing(false) : pricing;
        rateLimit = rateLimit == null ? new RateLimit(60, List.of()) : rateLimit;
        frontend = frontend == null ? new Frontend(null) : frontend;
    }

    /** @param snapshotDir directory produced by scripts/fetch-opendb.sh; may be null in production if ingestion runs as a job */
    public record OpenDb(Path snapshotDir, boolean ingestOnStartup) {
    }

    /** @param examplePrices enables fictitious development prices; must be false once real providers exist */
    public record Pricing(boolean examplePrices) {
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
