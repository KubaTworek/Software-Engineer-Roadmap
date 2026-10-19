package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import java.util.Objects;
import java.util.Optional;

/**
 * Models a downstream resource that enforces fencing tokens. Merely acquiring
 * a distributed lock is insufficient if the protected database or service
 * accepts commands from an owner whose lease already expired.
 */
public final class FencedRegister<T> {

    private long highestAcceptedToken;
    private T value;

    public synchronized void write(Lease lease, T newValue) {
        Objects.requireNonNull(lease, "lease must not be null");
        Objects.requireNonNull(newValue, "newValue must not be null");

        if (lease.fencingToken() < highestAcceptedToken) {
            throw new StaleFencingTokenException(lease.fencingToken(), highestAcceptedToken);
        }

        highestAcceptedToken = lease.fencingToken();
        value = newValue;
    }

    public synchronized Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public synchronized long highestAcceptedToken() {
        return highestAcceptedToken;
    }
}
