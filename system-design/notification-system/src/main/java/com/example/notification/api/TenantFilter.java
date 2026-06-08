package com.example.notification.api;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TenantFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        String tenantId = http.getHeader("X-Tenant-Id");
        if (tenantId == null || tenantId.isBlank()) tenantId = "default";

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
