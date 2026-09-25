package app.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window limit per client for write/compute endpoints (POST). In-memory: fine for a single instance;
 * behind a load balancer with several instances this moves to the gateway (e.g. AWS WAF rate rules).
 *
 * <p>The client key is the visitor's address reported by the frontend (requests proven by {@link FrontendGate}),
 * otherwise the socket address. X-Forwarded-For is honored only when the request comes from a configured trusted
 * proxy, because any client can set that header.
 */
@Component
class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final int limit;
    private final Set<String> trustedProxies;
    private final FrontendGate gate;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    RateLimitFilter(PlatformProperties properties, FrontendGate gate) {
        this.gate = gate;
        this.limit = properties.rateLimit().requestsPerMinute();
        this.trustedProxies = Set.copyOf(properties.rateLimit().trustedProxies());
    }

    private record Window(long start, int count) {
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().start() > WINDOW_MILLIS);
        }
        Window window = windows.compute(clientKey(request), (key, current) ->
                current == null || now - current.start() > WINDOW_MILLIS ? new Window(now, 1) : new Window(current.start(), current.count() + 1));
        if (window.count() > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(Math.max(1, (WINDOW_MILLIS - (now - window.start())) / 1000)));
            response.setContentType("application/problem+json;charset=UTF-8");
            response.getWriter().write("{\"title\":\"Muitas solicitações\",\"status\":429,"
                    + "\"detail\":\"Você fez muitas solicitações em pouco tempo. Aguarde um minuto e tente de novo.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String reported = gate.reportedClientAddress(request);
        if (reported != null) {
            return reported;
        }
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && trustedProxies.contains(remote)) {
            String client = forwarded.split(",")[0].trim();
            if (!client.isEmpty()) {
                return client;
            }
        }
        return remote;
    }
}
