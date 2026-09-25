package app.platform.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Recognizes requests relayed by the web frontend's server. The browser never talks to this API directly:
 * the frontend proxies {@code /api/*}, adds the shared secret and reports the visitor's address.
 */
@Component
class FrontendGate {

    static final String SECRET_HEADER = "X-Frontend-Secret";
    static final String CLIENT_ADDRESS_HEADER = "X-Client-IP";

    private final byte[] secret;

    FrontendGate(PlatformProperties properties) {
        String configured = properties.frontend().sharedSecret();
        this.secret = configured == null ? null : configured.getBytes(StandardCharsets.UTF_8);
    }

    /** Whether the API only answers the frontend (a secret is configured). */
    boolean enforced() {
        return secret != null;
    }

    boolean isFrontend(HttpServletRequest request) {
        String presented = request.getHeader(SECRET_HEADER);
        // Constant-time comparison: response timing must not reveal how much of the secret matched.
        return secret != null && presented != null
                && MessageDigest.isEqual(secret, presented.getBytes(StandardCharsets.UTF_8));
    }

    /** The visitor's address as reported by the frontend, or {@code null} when the request is not from the frontend. */
    String reportedClientAddress(HttpServletRequest request) {
        if (!isFrontend(request)) {
            return null;
        }
        String address = request.getHeader(CLIENT_ADDRESS_HEADER);
        return address == null || address.isBlank() ? null : address.trim();
    }
}
