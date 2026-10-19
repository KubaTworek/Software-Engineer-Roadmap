package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

/** Capacity model: HTTP/1.1 normally leases a connection, HTTP/2 leases a stream. */
public final class ProtocolCapacity {

    public enum Protocol {
        HTTP_1_1,
        HTTP_2
    }

    private ProtocolCapacity() {}

    public static int requiredConnections(Protocol protocol, int concurrentRequests, int maxConcurrentStreams) {
        if (concurrentRequests < 0) throw new IllegalArgumentException("concurrentRequests cannot be negative");
        if (concurrentRequests == 0) return 0;
        if (protocol == Protocol.HTTP_1_1) return concurrentRequests;
        if (maxConcurrentStreams < 1) throw new IllegalArgumentException("HTTP/2 requires a positive stream limit");
        return Math.ceilDiv(concurrentRequests, maxConcurrentStreams);
    }
}
