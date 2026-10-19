package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.time.Instant;
import java.util.Objects;

public record BackupSnapshot(
        Instant createdAt,
        String region,
        String schemaVersion,
        long recordCount,
        String checksum) {

    public BackupSnapshot {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(checksum, "checksum");
    }
}
