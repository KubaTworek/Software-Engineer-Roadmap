package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A single-authority model of lease acquisition. Synchronization only makes
 * this in-memory teaching model atomic; a production coordinator needs a
 * linearizable compare-and-set operation in shared durable infrastructure.
 */
public final class InMemoryLeaseCoordinator {

    private final Clock authorityClock;
    private final Map<String, LeaseState> states = new HashMap<>();

    public InMemoryLeaseCoordinator(Clock authorityClock) {
        this.authorityClock = Objects.requireNonNull(authorityClock, "authorityClock must not be null");
    }

    public synchronized Optional<Lease> tryAcquire(
            String resource,
            String owner,
            Duration duration
    ) {
        validate(resource, owner, duration);
        Instant now = authorityClock.instant();
        LeaseState state = states.computeIfAbsent(resource, ignored -> new LeaseState());

        if (state.current != null && !state.current.isExpiredAt(now)) {
            return Optional.empty();
        }

        long token = Math.incrementExact(state.lastIssuedToken);
        state.lastIssuedToken = token;
        state.current = new Lease(resource, owner, token, now.plus(duration));
        return Optional.of(state.current);
    }

    public synchronized Optional<Lease> renew(Lease lease, Duration duration) {
        Objects.requireNonNull(lease, "lease must not be null");
        validate(lease.resource(), lease.owner(), duration);
        LeaseState state = states.get(lease.resource());
        Instant now = authorityClock.instant();

        if (state == null
                || state.current == null
                || state.current.isExpiredAt(now)
                || !sameTerm(state.current, lease)) {
            return Optional.empty();
        }

        state.current = new Lease(
                lease.resource(),
                lease.owner(),
                lease.fencingToken(),
                now.plus(duration)
        );
        return Optional.of(state.current);
    }

    public synchronized boolean release(Lease lease) {
        Objects.requireNonNull(lease, "lease must not be null");
        LeaseState state = states.get(lease.resource());
        if (state == null || state.current == null || !sameTerm(state.current, lease)) {
            return false;
        }
        state.current = null;
        return true;
    }

    public synchronized Optional<Lease> currentLease(String resource) {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("resource is required");
        }
        LeaseState state = states.get(resource);
        if (state == null || state.current == null || state.current.isExpiredAt(authorityClock.instant())) {
            return Optional.empty();
        }
        return Optional.of(state.current);
    }

    private static boolean sameTerm(Lease current, Lease candidate) {
        return current.owner().equals(candidate.owner())
                && current.fencingToken() == candidate.fencingToken();
    }

    private static void validate(String resource, String owner, Duration duration) {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("resource is required");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner is required");
        }
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }

    private static final class LeaseState {
        private long lastIssuedToken;
        private Lease current;
    }
}
