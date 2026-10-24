package pl.jakubtworek.backend.common.operations;

import java.time.Duration;

/** Decyzja odczytu katalogu przy wymaganej świeżości; rezerwacje zawsze zapisuje primary. */
public final class ReplicaReadPolicy {
    public enum Target { PRIMARY, REPLICA, REJECT }
    public Target select(boolean primaryAvailable, boolean replicaAvailable, Duration observedLag,
                         Duration maxLag, boolean readYourWrites) {
        if (maxLag == null || maxLag.isNegative()) throw new IllegalArgumentException("Budżet lag");
        if (readYourWrites) return primaryAvailable ? Target.PRIMARY : Target.REJECT;
        // Brak lub ujemny pomiar nie może udawać repliki z zerowym opóźnieniem.
        if (replicaAvailable && observedLag != null && !observedLag.isNegative() && observedLag.compareTo(maxLag) <= 0)
            return Target.REPLICA;
        return primaryAvailable ? Target.PRIMARY : Target.REJECT;
    }
}
