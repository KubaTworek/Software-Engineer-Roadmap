package com.example.observability.server.auth;

import java.util.Set;

public record AuthContext(String tenantId, String apiKeyName, Set<String> roles) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean canRead() {
        return hasRole("viewer") || canWrite() || canAdmin();
    }

    public boolean canWrite() {
        return hasRole("writer") || hasRole("editor") || canAdmin();
    }

    public boolean canAdmin() {
        return hasRole("admin");
    }
}
