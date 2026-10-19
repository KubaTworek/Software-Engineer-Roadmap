package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Models client ephemeral ports retained in TIME_WAIT for one destination tuple. */
public final class EphemeralPortPool {

    private final Clock clock;
    private final int firstPort;
    private final int lastPort;
    private final Duration timeWait;
    private final Map<Integer, Instant> unavailableUntil = new HashMap<>();

    public EphemeralPortPool(Clock clock, int firstPort, int lastPort, Duration timeWait) {
        if (firstPort < 1 || lastPort > 65_535 || firstPort > lastPort) {
            throw new IllegalArgumentException("invalid ephemeral port range");
        }
        if (timeWait == null || timeWait.isNegative() || timeWait.isZero()) {
            throw new IllegalArgumentException("timeWait must be positive");
        }
        this.clock = clock;
        this.firstPort = firstPort;
        this.lastPort = lastPort;
        this.timeWait = timeWait;
    }

    public synchronized OptionalInt openConnection() {
        Instant now = clock.instant();
        for (int port = firstPort; port <= lastPort; port++) {
            Instant blockedUntil = unavailableUntil.get(port);
            if (blockedUntil == null || !now.isBefore(blockedUntil)) {
                unavailableUntil.put(port, Instant.MAX);
                return OptionalInt.of(port);
            }
        }
        return OptionalInt.empty();
    }

    public synchronized void closeConnection(int port) {
        if (!unavailableUntil.containsKey(port) || !Instant.MAX.equals(unavailableUntil.get(port))) {
            throw new IllegalArgumentException("port does not represent an open connection");
        }
        unavailableUntil.put(port, clock.instant().plus(timeWait));
    }

    public synchronized int unavailablePorts() {
        Instant now = clock.instant();
        return (int) unavailableUntil.values().stream().filter(until -> now.isBefore(until)).count();
    }
}
