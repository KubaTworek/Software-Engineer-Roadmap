package com.example.ratelimiter.security;

import com.example.ratelimiter.config.RateLimiterProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.stream.Stream;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Minimal control-plane boundary; production should replace it with IAM/mTLS. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminAuthenticationFilter extends OncePerRequestFilter {
    private final RateLimiterProperties properties;

    public AdminAuthenticationFilter(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Admin-Token");
        var security = properties.getSecurity();
        boolean accepted = Stream.concat(Stream.of(security.getAdminToken()), security.getPreviousAdminTokens().stream())
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> constantTime(value, supplied));
        if (!accepted) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean constantTime(String expected, String supplied) {
        if (supplied == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
