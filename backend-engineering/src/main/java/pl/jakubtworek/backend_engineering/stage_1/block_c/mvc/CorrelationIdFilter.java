package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

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

    static final String HEADER_NAME = "X-Correlation-Id";
    static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

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
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestedId = httpRequest.getHeader(HEADER_NAME);
        String correlationId = isSafe(requestedId)
                ? requestedId
                : UUID.randomUUID().toString();

        httpResponse.setHeader(HEADER_NAME, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static boolean isSafe(String candidate) {
        return candidate != null && SAFE_CORRELATION_ID.matcher(candidate).matches();
    }
}
