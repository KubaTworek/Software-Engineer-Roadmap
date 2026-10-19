package com.example.notification.api;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

@Component
public class TenantFilter implements Filter {
    private final Environment environment;
    public TenantFilter(Environment environment) { this.environment = environment; }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        String path = http.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/v1/provider-webhooks")) {
            chain.doFilter(request, response);
            return;
        }
        boolean secure = java.util.Arrays.asList(environment.getActiveProfiles()).contains("secure-runtime");
        String tenantId = secure && SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getClaimAsString("tenant_id") : http.getHeader("X-Tenant-Id");
        if (tenantId == null || !tenantId.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,99}")) {
            ((HttpServletResponse) response).sendError(400, "A valid X-Tenant-Id is required");
            return;
        }
        if (!secure && path.startsWith("/api/v1/admin/")
                && !"ADMIN".equalsIgnoreCase(http.getHeader("X-Role"))) {
            ((HttpServletResponse) response).sendError(403, "ADMIN role is required");
            return;
        }

        try {
            TenantContext.setTenantId(tenantId);
            MDC.put("tenantId", tenantId);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }
}
