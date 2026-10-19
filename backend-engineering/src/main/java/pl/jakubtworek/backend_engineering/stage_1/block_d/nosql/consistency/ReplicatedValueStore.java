package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.consistency;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Deterministyczny model leadera i opóźnionej repliki.
 * Pozwala zobaczyć stale read bez zależności od czasu i sieci.
 */
public final class ReplicatedValueStore<T> {

    private VersionedValue<T> leader;
    private VersionedValue<T> replica;
    private final Deque<VersionedValue<T>> replicationQueue = new ArrayDeque<>();

    public ReplicatedValueStore(T initialValue) {
        leader = new VersionedValue<>(initialValue, 0);
        replica = leader;
    }

    public synchronized ConsistencyToken write(T newValue) {
        leader = new VersionedValue<>(Objects.requireNonNull(newValue), leader.version() + 1);
        replicationQueue.addLast(leader);
        return new ConsistencyToken(leader.version());
    }

    public synchronized VersionedValue<T> readLeader() {
        return leader;
    }

    public synchronized VersionedValue<T> readReplica() {
        return replica;
    }

    /**
     * Jeśli replika nie dogoniła wersji zapisanej przez klienta, odczyt jest
     * kierowany do leadera. Token daje gwarancję sesyjną, a nie globalną.
     */
    public synchronized VersionedValue<T> readYourWrites(ConsistencyToken token) {
        Objects.requireNonNull(token, "token must not be null");
        if (token.minimumVersion() > leader.version()) {
            throw new IllegalStateException("store cannot satisfy token version " + token.minimumVersion());
        }
        return replica.version() >= token.minimumVersion() ? replica : leader;
    }

    public synchronized boolean replicateNext() {
        VersionedValue<T> next = replicationQueue.pollFirst();
        if (next == null) {
            return false;
        }
        replica = next;
        return true;
    }

    public synchronized int pendingReplications() {
        return replicationQueue.size();
    }

    public record ConsistencyToken(long minimumVersion) {
        public ConsistencyToken {
            if (minimumVersion < 0) {
                throw new IllegalArgumentException("minimumVersion must not be negative");
            }
        }
    }
}
