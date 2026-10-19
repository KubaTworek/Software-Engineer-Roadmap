package pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.idempotency;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal state machine for an idempotent operation.
 *
 * <p>The protocol distinguishes an operation that owns the right to start from
 * a retry that observes work in progress, a replay of a completed result and a
 * conflicting reuse of the same key. Keeping these outcomes explicit makes the
 * state transitions suitable for model-based and mutation testing.
 */
public final class IdempotencyProtocol {

    private final Map<String, Entry> entries = new HashMap<>();

    public synchronized BeginDecision begin(String key, String fingerprint) {
        requireText(key, "key");
        requireText(fingerprint, "fingerprint");

        Entry existing = entries.get(key);
        if (existing == null) {
            entries.put(key, Entry.processing(fingerprint));
            return new BeginDecision(BeginStatus.STARTED, Optional.empty());
        }
        if (!existing.fingerprint().equals(fingerprint)) {
            return new BeginDecision(BeginStatus.CONFLICT, Optional.empty());
        }
        if (existing.result() == null) {
            return new BeginDecision(BeginStatus.IN_PROGRESS, Optional.empty());
        }
        return new BeginDecision(BeginStatus.REPLAY, Optional.of(existing.result()));
    }

    public synchronized CompletionStatus complete(String key, String fingerprint, String result) {
        requireText(key, "key");
        requireText(fingerprint, "fingerprint");
        Objects.requireNonNull(result, "result");

        Entry existing = entries.get(key);
        if (existing == null) {
            throw new IllegalStateException("operation must be started before completion");
        }
        if (!existing.fingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException("key belongs to another request fingerprint");
        }
        if (existing.result() != null) {
            if (!existing.result().equals(result)) {
                throw new IdempotencyConflictException("completed result cannot be replaced");
            }
            return CompletionStatus.ALREADY_COMPLETED;
        }

        entries.put(key, existing.complete(result));
        return CompletionStatus.COMPLETED;
    }

    public synchronized Optional<ProtocolSnapshot> snapshot(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new ProtocolSnapshot(
                entry.fingerprint(),
                entry.result() == null ? ProtocolState.PROCESSING : ProtocolState.COMPLETED,
                Optional.ofNullable(entry.result())
        ));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public enum BeginStatus {
        STARTED,
        IN_PROGRESS,
        REPLAY,
        CONFLICT
    }

    public enum CompletionStatus {
        COMPLETED,
        ALREADY_COMPLETED
    }

    public enum ProtocolState {
        PROCESSING,
        COMPLETED
    }

    public record BeginDecision(BeginStatus status, Optional<String> replayedResult) {

        public BeginDecision {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(replayedResult, "replayedResult");
            if ((status == BeginStatus.REPLAY) != replayedResult.isPresent()) {
                throw new IllegalArgumentException("only REPLAY carries a result");
            }
        }
    }

    public record ProtocolSnapshot(
            String fingerprint,
            ProtocolState state,
            Optional<String> result
    ) {
    }

    public static final class IdempotencyConflictException extends RuntimeException {

        public IdempotencyConflictException(String message) {
            super(message);
        }
    }

    private record Entry(String fingerprint, String result) {

        static Entry processing(String fingerprint) {
            return new Entry(fingerprint, null);
        }

        Entry complete(String result) {
            return new Entry(fingerprint, result);
        }
    }
}
