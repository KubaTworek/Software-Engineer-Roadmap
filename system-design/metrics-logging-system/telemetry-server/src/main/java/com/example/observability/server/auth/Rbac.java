package com.example.observability.server.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Rbac {
    private Rbac() {
    }

    public static AuthContext current() {
        AuthContext ctx = AuthContextHolder.get();
        if (ctx == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing auth context");
        return ctx;
    }

    public static AuthContext requirePlatformAdmin() {
        AuthContext ctx = current();
        if (!ctx.canAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin access denied");
        return ctx;
    }

    public static AuthContext requireRead(String tenantId) {
        AuthContext ctx = AuthContextHolder.get();
        if (ctx == null || !ctx.canRead() || !ctx.tenantId().equals(tenantId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "read access denied");
        return ctx;
    }

    public static AuthContext requireWrite(String tenantId) {
        AuthContext ctx = AuthContextHolder.get();
        if (ctx == null || !ctx.canWrite() || !ctx.tenantId().equals(tenantId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "write access denied");
        return ctx;
    }

    public static AuthContext requireAdmin(String tenantId) {
        AuthContext ctx = AuthContextHolder.get();
        if (ctx == null || !ctx.canAdmin() || !ctx.tenantId().equals(tenantId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin access denied");
        return ctx;
    }
}
