package pl.jakubtworek.backend.gateway.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties properties;
    private final RedisTokenBucketRateLimiter rateLimiter;

    public RateLimitFilter(RateLimitProperties properties, RedisTokenBucketRateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = Optional.ofNullable(request.getHeader("X-API-Key")).filter(v -> !v.isBlank()).orElse(null);
        String identity = apiKey != null ? "api-key:" + apiKey : "ip:" + clientIp(request);
        RateLimitProperties.Bucket bucket = apiKey != null ? properties.apiKey() : properties.anonymous();
        String redisKey = "rate-limit:" + identity + ":" + request.getMethod() + ":" + request.getRequestURI();

        try {
            TokenBucketDecision decision = rateLimiter.consume(redisKey, bucket);
            response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remainingTokens()));
            if (!decision.allowed()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests\"}");
                return;
            }
        } catch (DataAccessException exception) {
            // Fail-open is a deliberate degradation decision: Redis outage should not take down read/write traffic.
            log.warn("Rate limiter Redis unavailable. Falling back to fail-open mode.", exception);
            response.setHeader("X-RateLimit-Degraded", "true");
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

@Configuration
class RateLimitFilterConfig {
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(3);
        return registration;
    }
}
