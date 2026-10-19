package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.lease;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents the storage-side check. Comparing and persisting the token and
 * value must be one atomic operation in a real database or external system.
 */
public final class FencedRegister<T> {

    private final String resourceId;
    private long lastAcceptedToken;
    private T value;

    public FencedRegister(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        this.resourceId = resourceId;
    }

    public synchronized void write(LeaseGrant grant, T newValue) {
        Objects.requireNonNull(grant, "grant must not be null");
        Objects.requireNonNull(newValue, "newValue must not be null");
        if (!resourceId.equals(grant.resourceId())) {
            throw new IllegalArgumentException("grant belongs to another resource");
        }
        if (grant.fencingToken() <= lastAcceptedToken) {
            throw new StaleFencingTokenException(grant.fencingToken(), lastAcceptedToken);
        }

        value = newValue;
        lastAcceptedToken = grant.fencingToken();
    }

    public synchronized Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public synchronized long lastAcceptedToken() {
        return lastAcceptedToken;
    }
}
