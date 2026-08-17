package com.example.videostreaming.audit;

import com.example.videostreaming.auth.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuditInterceptor implements HandlerInterceptor {
    private final AuditLogRepository auditLogs;

    public AdminAuditInterceptor(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!isAdminMutation(request)) return;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) return;
        String[] parts = request.getRequestURI().split("/");
        String resourceType = parts.length > 2 ? parts[2] : null;
        String resourceId = parts.length > 3 ? parts[3] : null;
        String action = request.getMethod() + " " + request.getRequestURI();
        auditLogs.save(new AuditLog(
                user.getId(), user.getEmail(), action, resourceType, resourceId,
                request.getMethod(), request.getRequestURI(), response.getStatus(), clientIp(request), request.getHeader("User-Agent")
        ));
    }

    private boolean isAdminMutation(HttpServletRequest request) {
        String method = request.getMethod();
        if (!(method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE"))) return false;
        String path = request.getRequestURI();
        return path.startsWith("/api/videos") || path.startsWith("/api/admin") || path.startsWith("/api/premium/admin") || path.startsWith("/admin");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
