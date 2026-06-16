package com.example.observability.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.observability.server.tenant.DynamicApiKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {
    private final ApiKeyProperties properties;
    private final DynamicApiKeyService dynamicApiKeyService;

    public ApiKeyAuthInterceptor(ApiKeyProperties properties, DynamicApiKeyService dynamicApiKeyService) {
        this.properties = properties;
        this.dynamicApiKeyService = dynamicApiKeyService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (!properties.isEnabled() || path.equals("/") || path.startsWith("/actuator") || path.startsWith("/css") || path.startsWith("/js") || path.startsWith("/favicon")) {
            AuthContextHolder.set(new AuthContext("demo", "anonymous", Set.of("admin", "writer", "viewer")));
            return true;
        }
        String token = request.getHeader("X-API-Key");
        if (token == null || token.isBlank()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) token = auth.substring("Bearer ".length());
        }
        var key = properties.findByToken(token);
        if (key.isPresent()) {
            AuthContextHolder.set(new AuthContext(key.get().getTenantId(), key.get().getName(), key.get().getRoles()));
            return true;
        }
        var dynamicKey = dynamicApiKeyService.find(token);
        if (dynamicKey.isPresent()) {
            AuthContextHolder.set(dynamicKey.get());
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.getWriter().write("Missing or invalid API key. Use X-API-Key header.");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContextHolder.clear();
    }
}
