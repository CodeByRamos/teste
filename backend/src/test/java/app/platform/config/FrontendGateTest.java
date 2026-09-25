package app.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendGateTest {

    private static PlatformProperties properties(String secret) {
        return new PlatformProperties(null, null, new PlatformProperties.RateLimit(2, List.of()), new PlatformProperties.Frontend(secret));
    }

    private static MockHttpServletRequest post(String secret, String clientAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/recommendations");
        request.setRemoteAddr("10.0.0.1");
        if (secret != null) {
            request.addHeader(FrontendGate.SECRET_HEADER, secret);
        }
        if (clientAddress != null) {
            request.addHeader(FrontendGate.CLIENT_ADDRESS_HEADER, clientAddress);
        }
        return request;
    }

    private static int status(OncePerRequest filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.run(request, response);
        return response.getStatus();
    }

    @FunctionalInterface
    private interface OncePerRequest {
        void run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception;
    }

    @Test
    void withSecretConfiguredOnlyTheFrontendIsAnswered() throws Exception {
        FrontendOnlyFilter filter = new FrontendOnlyFilter(new FrontendGate(properties("s3cret")));
        OncePerRequest run = (request, response) -> filter.doFilter(request, response, new MockFilterChain());

        assertThat(status(run, post(null, null))).isEqualTo(403);
        assertThat(status(run, post("wrong", null))).isEqualTo(403);
        assertThat(status(run, post("s3cret", null))).isEqualTo(200);
        MockHttpServletRequest health = new MockHttpServletRequest("GET", "/actuator/health/readiness");
        assertThat(status(run, health)).isEqualTo(200);
    }

    @Test
    void withoutSecretEverythingPassesAsInLocalDevelopment() throws Exception {
        FrontendOnlyFilter filter = new FrontendOnlyFilter(new FrontendGate(properties(null)));

        assertThat(status((request, response) -> filter.doFilter(request, response, new MockFilterChain()), post(null, null)))
                .isEqualTo(200);
    }

    @Test
    void rateLimitCountsEachVisitorReportedByTheFrontendSeparately() throws Exception {
        FrontendGate gate = new FrontendGate(properties("s3cret"));
        RateLimitFilter filter = new RateLimitFilter(properties("s3cret"), gate);
        OncePerRequest run = (request, response) -> filter.doFilter(request, response, new MockFilterChain());

        // Same socket address (the proxy), different visitors: limit is 2 per visitor.
        assertThat(status(run, post("s3cret", "203.0.113.1"))).isEqualTo(200);
        assertThat(status(run, post("s3cret", "203.0.113.1"))).isEqualTo(200);
        assertThat(status(run, post("s3cret", "203.0.113.1"))).isEqualTo(429);
        assertThat(status(run, post("s3cret", "203.0.113.2"))).isEqualTo(200);
        // A forged visitor address without the secret is ignored: keyed by the socket address instead.
        assertThat(status(run, post(null, "203.0.113.9"))).isEqualTo(200);
        assertThat(status(run, post(null, "203.0.113.10"))).isEqualTo(200);
        assertThat(status(run, post(null, "203.0.113.11"))).isEqualTo(429);
    }
}
