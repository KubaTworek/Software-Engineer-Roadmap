package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.time.Duration;
import java.util.Objects;

public record RestoreResult(
        Duration duration,
        String schemaVersion,
        long recordCount,
        String checksum,
        boolean applicationSmokeTestPassed) {

    public RestoreResult {
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(checksum, "checksum");
    }
}
