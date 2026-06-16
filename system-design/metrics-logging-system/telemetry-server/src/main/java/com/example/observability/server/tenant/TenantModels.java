package com.example.observability.server.tenant;

import java.time.Instant;
import java.util.Set;

public final class TenantModels {
    private TenantModels() {
    }

    public record Tenant(String tenantId, String displayName, String status, String plan, String primaryRegion,
                         int retentionDays, Instant createdAt, Instant updatedAt) {
    }

    public record CreateTenantRequest(String tenantId, String displayName, String plan, String primaryRegion,
                                      Integer retentionDays) {
    }

    public record UpdateTenantRequest(String displayName, String status, String plan, String primaryRegion,
                                      Integer retentionDays) {
    }

    public record ApiKeyView(String tenantId, String keyId, String name, Set<String> roles, String status,
                             Instant createdAt, Instant expiresAt) {
    }

    public record CreateApiKeyRequest(String name, Set<String> roles, Instant expiresAt) {
    }

    public record CreatedApiKey(String tenantId, String keyId, String token, String name, Set<String> roles) {
    }
}
