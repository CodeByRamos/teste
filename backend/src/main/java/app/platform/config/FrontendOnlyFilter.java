package app.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** When a frontend secret is configured, rejects {@code /api/**} calls that do not come through the frontend. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class FrontendOnlyFilter extends OncePerRequestFilter {

    private final FrontendGate gate;

    FrontendOnlyFilter(FrontendGate gate) {
        this.gate = gate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !gate.enforced() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (gate.isFrontend(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter().write("{\"title\":\"Acesso negado\",\"status\":403,"
                + "\"detail\":\"Esta API atende apenas o site.\"}");
    }
}
