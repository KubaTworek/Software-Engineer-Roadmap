package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet Filter runs before request reaches DispatcherServlet.
 *
 * Filters are useful for low-level concerns:
 * - request logging,
 * - correlation ids,
 * - security preprocessing,
 * - encoding,
 * - CORS.
 */
@Component
public class CorrelationIdFilter implements Filter {

    /**
     * This method wraps the whole servlet processing chain.
     *
     * DispatcherServlet is executed inside filterChain.doFilter().
     */
    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain filterChain
    ) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String correlationId = httpRequest.getHeader("X-Correlation-Id");

        if (correlationId == null) {
            correlationId = java.util.UUID.randomUUID().toString();
        }

        System.out.println("Correlation ID: " + correlationId);

        filterChain.doFilter(request, response);
    }
}