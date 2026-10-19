package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToLongFunction;

/**
 * Consistent hash ring with virtual nodes. Adding a physical node moves only
 * the key ranges now owned by that node instead of recalculating every key
 * with modulo nodeCount.
 */
public final class ConsistentHashRing {

    private final int virtualNodesPerNode;
    private final ToLongFunction<String> hashFunction;
    private final NavigableMap<Long, String> ring = new TreeMap<>();
    private final Map<String, List<Long>> pointsByNode = new HashMap<>();

    public ConsistentHashRing(int virtualNodesPerNode) {
        this(virtualNodesPerNode, ConsistentHashRing::sha256Point);
    }

    ConsistentHashRing(int virtualNodesPerNode, ToLongFunction<String> hashFunction) {
        if (virtualNodesPerNode <= 0) {
            throw new IllegalArgumentException("virtualNodesPerNode must be positive");
        }
        this.virtualNodesPerNode = virtualNodesPerNode;
        this.hashFunction = Objects.requireNonNull(hashFunction, "hashFunction must not be null");
    }

    public synchronized boolean addNode(String nodeId) {
        validateValue(nodeId, "nodeId");
        if (pointsByNode.containsKey(nodeId)) {
            return false;
        }

        List<Long> points = new ArrayList<>(virtualNodesPerNode);
        for (int replica = 0; replica < virtualNodesPerNode; replica++) {
            long point = normalizedHash(nodeId + "#" + replica);
            while (ring.containsKey(point)) {
                point = nextPoint(point);
            }
            ring.put(point, nodeId);
            points.add(point);
        }
        pointsByNode.put(nodeId, List.copyOf(points));
        return true;
    }

    public synchronized boolean removeNode(String nodeId) {
        validateValue(nodeId, "nodeId");
        List<Long> points = pointsByNode.remove(nodeId);
        if (points == null) {
            return false;
        }
        points.forEach(ring::remove);
        return true;
    }

    public synchronized Optional<String> ownerOf(String key) {
        validateValue(key, "key");
        if (ring.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<Long, String> owner = ring.ceilingEntry(normalizedHash(key));
        return Optional.of((owner != null ? owner : ring.firstEntry()).getValue());
    }

    public synchronized Set<String> nodes() {
        return Set.copyOf(pointsByNode.keySet());
    }

    private long normalizedHash(String value) {
        return hashFunction.applyAsLong(value) & Long.MAX_VALUE;
    }

    private static long nextPoint(long point) {
        return point == Long.MAX_VALUE ? 0 : point + 1;
    }

    private static long sha256Point(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in every Java runtime", exception);
        }
    }

    private static void validateValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
