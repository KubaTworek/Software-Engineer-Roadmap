package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded keep-alive pool that never returns expired or known half-closed connections. */
public final class ConnectionPool {

    public record Connection(long id, Instant createdAt, Instant lastUsedAt, boolean reusable) {
        Connection usedAt(Instant instant) {
            return new Connection(id, createdAt, instant, reusable);
        }

        Connection nonReusable() {
            return new Connection(id, createdAt, lastUsedAt, false);
        }
    }

    private final Clock clock;
    private final int maxConnections;
    private final Duration maxIdle;
    private final Duration maxLifetime;
    private final AtomicLong ids = new AtomicLong();
    private final ArrayDeque<Connection> idle = new ArrayDeque<>();
    private final Set<Long> leased = new HashSet<>();
    private int openConnections;

    public ConnectionPool(Clock clock, int maxConnections, Duration maxIdle, Duration maxLifetime) {
        if (maxConnections < 1) throw new IllegalArgumentException("maxConnections must be positive");
        if (maxIdle.isNegative() || maxIdle.isZero()) throw new IllegalArgumentException("maxIdle must be positive");
        if (maxLifetime.isNegative() || maxLifetime.isZero()) throw new IllegalArgumentException("maxLifetime must be positive");
        this.clock = clock;
        this.maxConnections = maxConnections;
        this.maxIdle = maxIdle;
        this.maxLifetime = maxLifetime;
    }

    public synchronized Optional<Connection> tryAcquire() {
        Instant now = clock.instant();
        while (!idle.isEmpty()) {
            Connection candidate = idle.removeFirst();
            if (!isUsable(candidate, now)) {
                openConnections--;
                continue;
            }
            Connection leasedConnection = candidate.usedAt(now);
            leased.add(leasedConnection.id());
            return Optional.of(leasedConnection);
        }
        if (openConnections == maxConnections) return Optional.empty();
        Connection created = new Connection(ids.incrementAndGet(), now, now, true);
        openConnections++;
        leased.add(created.id());
        return Optional.of(created);
    }

    public synchronized void release(Connection connection, boolean responseCompletedCleanly) {
        if (!leased.remove(connection.id())) throw new IllegalArgumentException("connection is not leased by this pool");
        Connection released = responseCompletedCleanly ? connection.usedAt(clock.instant()) : connection.nonReusable();
        if (isUsable(released, clock.instant())) idle.addLast(released);
        else openConnections--;
    }

    public synchronized int openConnections() {
        return openConnections;
    }

    private boolean isUsable(Connection connection, Instant now) {
        return connection.reusable()
                && Duration.between(connection.lastUsedAt(), now).compareTo(maxIdle) < 0
                && Duration.between(connection.createdAt(), now).compareTo(maxLifetime) < 0;
    }
}
