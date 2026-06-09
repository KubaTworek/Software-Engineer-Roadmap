package com.example.newsfeed.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimitService rateLimitService, RateLimitProperties properties) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ip = request.getRemoteAddr();
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("GET".equals(method) && path.startsWith("/api/v1/feed")) {
            rateLimitService.check("feed", ip, properties.feedPerMinute());
        } else if ("POST".equals(method) && path.equals("/api/v1/posts")) {
            rateLimitService.check("post-create", ip, properties.postCreatePerMinute());
        } else if (!"GET".equals(method)) {
            rateLimitService.check("write", ip, properties.writePerMinute());
        }

        return true;
    }
}
