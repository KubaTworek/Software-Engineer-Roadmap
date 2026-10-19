package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.util.Objects;

/** Cache namespace that makes cross-tenant key collisions impossible by construction. */
public record TenantCacheKey(String namespace, TenantId tenantId, String resourceId) {

    public TenantCacheKey {
        requireSegment(namespace, "namespace");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        requireSegment(resourceId, "resourceId");
    }

    public String serialized() {
        return namespace + ":tenant:" + tenantId.value() + ":resource:" + resourceId;
    }

    private static void requireSegment(String value, String name) {
        if (value == null || value.isBlank() || value.contains(":")) {
            throw new IllegalArgumentException(name + " must be a non-blank cache-key segment without ':'");
        }
    }
}
