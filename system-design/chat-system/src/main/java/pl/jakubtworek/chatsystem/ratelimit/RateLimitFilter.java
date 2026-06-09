package pl.jakubtworek.chatsystem.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final InMemoryRateLimiter rateLimiter;

    public RateLimitFilter(InMemoryRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/auth/") || path.contains("/attachments/") && path.endsWith("/content")) {
            filterChain.doFilter(request, response);
            return;
        }

        String identity = currentIdentity(request);
        Limit limit = chooseLimit(request.getMethod(), path);
        if (!rateLimiter.allow(identity + ":" + limit.name, limit.maxRequests, limit.windowSeconds)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Rate limit exceeded\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String currentIdentity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return "user:" + principal.id();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Limit chooseLimit(String method, String path) {
        if ("POST".equals(method) && path.contains("/messages")) {
            return new Limit("send-message", 30, 60);
        }
        if ("POST".equals(method) && path.contains("/upload-url")) {
            return new Limit("attachment-create", 20, 60);
        }
        if ("GET".equals(method) && path.contains("/search")) {
            return new Limit("search", 60, 60);
        }
        return new Limit("api", 300, 60);
    }

    private record Limit(String name, int maxRequests, long windowSeconds) {}
}
