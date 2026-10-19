package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.util.List;

/** Makes encryption ownership explicit for every transport hop and persistent copy. */
public record DataProtectionBoundary(
        String dataSet,
        SecurityDataFlow.DataSensitivity sensitivity,
        List<TransportHop> transportHops,
        List<StorageCopy> storageCopies) {

    public DataProtectionBoundary {
        if (dataSet == null || dataSet.isBlank()) throw new IllegalArgumentException("dataSet is required");
        if (sensitivity == null) throw new IllegalArgumentException("sensitivity is required");
        transportHops = List.copyOf(transportHops);
        storageCopies = List.copyOf(storageCopies);
        if (transportHops.isEmpty()) throw new IllegalArgumentException("at least one transport hop is required");
    }

    public record TransportHop(String name, boolean encrypted, String certificateOwner) {
    }

    public record StorageCopy(String name, boolean encrypted, String keyOwner) {
    }
}
