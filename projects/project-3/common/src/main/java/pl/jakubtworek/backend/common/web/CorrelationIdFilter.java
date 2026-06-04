package pl.jakubtworek.backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader(CorrelationId.CORRELATION_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElse(UUID.randomUUID().toString());
        String requestId = Optional.ofNullable(request.getHeader(CorrelationId.REQUEST_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElse(UUID.randomUUID().toString());

        MDC.put(CorrelationId.MDC_CORRELATION_ID, correlationId);
        MDC.put(CorrelationId.MDC_REQUEST_ID, requestId);
        response.setHeader(CorrelationId.CORRELATION_ID_HEADER, correlationId);
        response.setHeader(CorrelationId.REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_CORRELATION_ID);
            MDC.remove(CorrelationId.MDC_REQUEST_ID);
        }
    }
}
