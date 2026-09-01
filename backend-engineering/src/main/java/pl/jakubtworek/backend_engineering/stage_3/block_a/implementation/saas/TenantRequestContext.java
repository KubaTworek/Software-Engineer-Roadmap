package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.util.Objects;

/** Context derived from authenticated claims, never directly from a user-supplied tenant header. */
public record TenantRequestContext(TenantId tenantId, String actorId, String purpose) {

    public TenantRequestContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        requireText(actorId, "actorId");
        requireText(purpose, "purpose");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
