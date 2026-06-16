package com.example.observability.server.auth;

public final class AuthContextHolder {
    private static final ThreadLocal<AuthContext> CURRENT = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void set(AuthContext context) {
        CURRENT.set(context);
    }

    public static AuthContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
