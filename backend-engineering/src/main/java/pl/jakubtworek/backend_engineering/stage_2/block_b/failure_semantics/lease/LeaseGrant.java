package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.lease;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public record LeaseGrant(String resourceId, String ownerId, long fencingToken, Instant expiresAt) {

    public LeaseGrant {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (fencingToken <= 0) {
            throw new IllegalArgumentException("fencingToken must be positive");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
    }

    public boolean isExpired(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return !clock.instant().isBefore(expiresAt);
    }
}
