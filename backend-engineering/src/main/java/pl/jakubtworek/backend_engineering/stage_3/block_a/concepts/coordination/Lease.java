package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import java.time.Instant;

/**
 * Time-bounded ownership granted by one authoritative coordinator.
 * The fencing token grows on every new ownership term and never resets.
 */
public record Lease(
        String resource,
        String owner,
        long fencingToken,
        Instant expiresAt
) {

    public Lease {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("resource is required");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner is required");
        }
        if (fencingToken <= 0) {
            throw new IllegalArgumentException("fencingToken must be positive");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
    }

    public boolean isExpiredAt(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("instant is required");
        }
        return !instant.isBefore(expiresAt);
    }
}
