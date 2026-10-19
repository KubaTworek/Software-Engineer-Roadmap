package com.example.urlshortener.api;

import com.example.urlshortener.config.RateLimitProperties;
import com.example.urlshortener.security.TrustedClientAddressResolver;
import com.example.urlshortener.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Maps HTTP operations to rate-limit keys without trusting caller-controlled proxy headers. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitService limits;
    private final RateLimitProperties properties;
    private final TrustedClientAddressResolver clients;

    public RateLimitFilter(RateLimitService limits, RateLimitProperties properties,
            TrustedClientAddressResolver clients) {
        this.limits = limits;
        this.properties = properties;
        this.clients = clients;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String client = clients.resolve(request);
        if ("POST".equals(request.getMethod()) && "/api/v1/urls".equals(path)) {
            check("create", client, properties.getCreate());
        } else if ("GET".equals(request.getMethod()) && isRedirectPath(path)) {
            check("redirect", client, properties.getRedirect());
        }
        chain.doFilter(request, response);
    }

    private void check(String operation, String client, RateLimitProperties.Limit limit) {
        limits.checkFixedWindow("rl:" + operation + ":" + client, limit.getRequests(), limit.getWindow());
    }

    private boolean isRedirectPath(String path) {
        return path.matches("/[A-Za-z0-9_-]{3,32}|/[A-Za-z0-9]{1,32}");
    }
}
