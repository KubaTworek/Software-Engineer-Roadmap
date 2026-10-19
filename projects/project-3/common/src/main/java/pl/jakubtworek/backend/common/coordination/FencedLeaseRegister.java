package pl.jakubtworek.backend.common.coordination;

import java.time.Duration;
import java.time.Instant;

/**
 * Deterministyczny model lease + fencing używany do wyjaśnienia poprawności.
 *
 * Lease mówi koordynatorowi, kto "powinien" być właścicielem. Nie zatrzymuje jednak
 * starego workera, który został wstrzymany przez GC, utracił lease i później wrócił.
 * Dopiero monotonically increasing fencing token, sprawdzany przez chroniony zasób,
 * odrzuca zapis starego właściciela. To rozróżnienie jest ważniejsze od wyboru biblioteki.
 */
public final class FencedLeaseRegister<T> {
    private long issuedToken;
    private long acceptedToken;
    private String owner;
    private Instant leaseUntil = Instant.MIN;
    private T value;

    public synchronized Lease acquire(String candidate, Instant now, Duration ttl) {
        if (now.isBefore(leaseUntil) && !candidate.equals(owner)) {
            throw new IllegalStateException("Lease is held by " + owner);
        }
        owner = candidate;
        leaseUntil = now.plus(ttl);
        return new Lease(candidate, ++issuedToken, leaseUntil);
    }

    public synchronized void write(Lease lease, T newValue) {
        if (lease.fencingToken() < acceptedToken) {
            throw new StaleOwnerException(lease.owner(), lease.fencingToken(), acceptedToken);
        }
        acceptedToken = lease.fencingToken();
        value = newValue;
    }

    public synchronized T value() { return value; }

    public record Lease(String owner, long fencingToken, Instant validUntil) { }

    public static final class StaleOwnerException extends IllegalStateException {
        public StaleOwnerException(String owner, long offered, long accepted) {
            super("Stale owner " + owner + " offered token " + offered + ", accepted token is " + accepted);
        }
    }
}
