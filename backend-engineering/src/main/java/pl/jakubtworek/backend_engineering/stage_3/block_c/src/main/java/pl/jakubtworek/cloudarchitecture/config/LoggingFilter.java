package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * Emits simple structured JSON logs for every HTTP request.
 *
 * Structured logs are easier to filter, aggregate, and correlate in Cloud Logging.
 */
@Component
public class LoggingFilter implements Filter {
    /** Measures request latency and logs request metadata. */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            long latencyMs = System.currentTimeMillis() - start;
            System.out.printf(
                    "{"severity":"INFO","method":"%s","path":"%s","status":%d,"latencyMs":%d}%n",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    httpResponse.getStatus(),
                    latencyMs
            );
        }
    }
}
