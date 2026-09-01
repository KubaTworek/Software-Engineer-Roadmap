package pl.jakubtworek.cloudarchitecture.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerMapping;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Emits simple structured JSON logs for every HTTP request.
 *
 * Structured logs are easier to filter, aggregate, and correlate in Cloud Logging.
 */
@Component
public class LoggingFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private final ObjectMapper objectMapper;

    public LoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /** Measures request latency and logs request metadata. */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String requestId = requestId(httpRequest.getHeader(REQUEST_ID_HEADER));
        httpResponse.setHeader(REQUEST_ID_HEADER, requestId);
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("severity", "INFO");
            event.put("event", "HTTP_REQUEST_COMPLETED");
            event.put("requestId", requestId);
            event.put("method", httpRequest.getMethod());
            Object routePattern = httpRequest.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            event.put("route", routePattern == null ? "unmatched" : routePattern.toString());
            event.put("status", httpResponse.getStatus());
            event.put("latencyMs", latencyMs);
            try {
                LOGGER.info("{}", objectMapper.writeValueAsString(event));
            } catch (JsonProcessingException exception) {
                LOGGER.warn("Could not serialize HTTP request log", exception);
            }
        }
    }

    private static String requestId(String candidate) {
        if (candidate != null
                && candidate.length() <= 128
                && candidate.matches("[A-Za-z0-9._:-]+")) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
