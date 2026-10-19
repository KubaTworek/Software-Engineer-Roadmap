package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Validates that an outer caller outlives the inner hop and its transport margin. */
public final class TimeoutChainValidator {

    public record Hop(String name, Duration timeout, Duration cleanupMargin) {
        public Hop {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("hop name is required");
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            if (cleanupMargin == null || cleanupMargin.isNegative()) {
                throw new IllegalArgumentException("cleanupMargin cannot be negative");
            }
        }
    }

    public List<String> validateOuterToInner(List<Hop> hops) {
        List<String> violations = new ArrayList<>();
        for (int index = 0; index < hops.size() - 1; index++) {
            Hop outer = hops.get(index);
            Hop inner = hops.get(index + 1);
            Duration required = inner.timeout().plus(outer.cleanupMargin());
            if (outer.timeout().compareTo(required) <= 0) {
                violations.add(outer.name() + " must exceed " + inner.name() + " timeout plus cleanup margin");
            }
        }
        return List.copyOf(violations);
    }
}
