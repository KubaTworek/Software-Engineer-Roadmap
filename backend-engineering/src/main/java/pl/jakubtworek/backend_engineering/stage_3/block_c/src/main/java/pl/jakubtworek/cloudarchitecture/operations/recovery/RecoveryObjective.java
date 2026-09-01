package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.time.Duration;
import java.util.Objects;

/** RPO limits acceptable data loss; RTO limits acceptable service downtime. */
public record RecoveryObjective(Duration rpo, Duration rto) {

    public RecoveryObjective {
        Objects.requireNonNull(rpo, "rpo");
        Objects.requireNonNull(rto, "rto");
    }
}
