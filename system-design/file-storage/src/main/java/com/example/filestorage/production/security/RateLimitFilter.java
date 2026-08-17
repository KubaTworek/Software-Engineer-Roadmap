package com.example.filestorage.production.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled() || request.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }
        String actor = request.getRemoteAddr() + ":" + request.getRequestURI();
        long currentMinute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(actor, (key, old) -> old == null || old.minute != currentMinute ? new Window(currentMinute) : old);
        int count = window.count.incrementAndGet();
        if (count > properties.requestsPerMinute()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Rate limit exceeded\"}");
            return;
        }
        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.requestsPerMinute()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, properties.requestsPerMinute() - count)));
        filterChain.doFilter(request, response);
    }

    private static class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();
        private Window(long minute) { this.minute = minute; }
    }
}
