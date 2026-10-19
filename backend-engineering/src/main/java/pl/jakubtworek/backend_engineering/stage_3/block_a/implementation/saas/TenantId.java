package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.util.Objects;

/**
 * Strong tenant identifier. Passing it explicitly keeps the isolation boundary
 * visible in repository, cache and event APIs.
 */
public record TenantId(String value) {

    public TenantId {
        Objects.requireNonNull(value, "value must not be null");
        if (!value.matches("[a-z0-9][a-z0-9-]{1,62}")) {
            throw new IllegalArgumentException("tenant id must be a stable, normalized identifier");
        }
    }
}
