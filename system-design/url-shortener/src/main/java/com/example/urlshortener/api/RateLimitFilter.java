package com.example.urlshortener.api;

import com.example.urlshortener.config.RateLimitProperties;
import com.example.urlshortener.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;

    public RateLimitFilter(RateLimitService rateLimitService, RateLimitProperties properties) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String clientId = clientId(request);

        if ("POST".equals(method) && "/api/v1/urls".equals(path)) {
            RateLimitProperties.Limit limit = properties.getCreate();
            rateLimitService.checkFixedWindow("rl:create:" + clientId, limit.getRequests(), limit.getWindow());
        } else if ("GET".equals(method) && isRedirectPath(path)) {
            RateLimitProperties.Limit limit = properties.getRedirect();
            rateLimitService.checkFixedWindow("rl:redirect:" + clientId, limit.getRequests(), limit.getWindow());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRedirectPath(String path) {
        return path.matches("/[A-Za-z0-9_-]{3,32}|/[A-Za-z0-9]{1,32}");
    }

    private String clientId(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
