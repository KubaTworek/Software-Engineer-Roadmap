package pl.jakubtworek.marketplace.integration.kafka;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Techniczny model eventu zapisanego w DLQ.
 */
public record DlqEvent(
        UUID id,
        String topic,
        String consumerGroup,
        long offset,
        IntegrationEventEnvelope envelope,
        String reason,
        int attempts,
        Instant failedAt,
        DlqEventStatus status,
        Instant replayedAt,
        String replayError
) {

    public DlqEvent {
        Objects.requireNonNull(id, "dlq event id cannot be null");
        Objects.requireNonNull(topic, "topic cannot be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
        Objects.requireNonNull(failedAt, "failedAt cannot be null");
        Objects.requireNonNull(status, "status cannot be null");

        if (topic.isBlank()) {
            throw new IllegalArgumentException("topic cannot be blank");
        }

        if (consumerGroup.isBlank()) {
            throw new IllegalArgumentException("consumerGroup cannot be blank");
        }

        if (attempts <= 0) {
            throw new IllegalArgumentException("attempts must be positive");
        }
    }

    public static DlqEvent newEvent(
            String topic,
            String consumerGroup,
            long offset,
            IntegrationEventEnvelope envelope,
            String reason,
            int attempts
    ) {
        return new DlqEvent(
                UUID.randomUUID(),
                topic,
                consumerGroup,
                offset,
                envelope,
                reason,
                attempts,
                Instant.now(),
                DlqEventStatus.NEW,
                null,
                null
        );
    }

    public static DlqEvent restore(
            UUID id,
            String topic,
            String consumerGroup,
            long offset,
            IntegrationEventEnvelope envelope,
            String reason,
            int attempts,
            Instant failedAt,
            DlqEventStatus status,
            Instant replayedAt,
            String replayError
    ) {
        return new DlqEvent(
                id,
                topic,
                consumerGroup,
                offset,
                envelope,
                reason,
                attempts,
                failedAt,
                status,
                replayedAt,
                replayError
        );
    }

    public DlqEvent markReplayed() {
        return new DlqEvent(
                id,
                topic,
                consumerGroup,
                offset,
                envelope,
                reason,
                attempts,
                failedAt,
                DlqEventStatus.REPLAYED,
                Instant.now(),
                null
        );
    }

    public DlqEvent markReplayFailed(String replayError) {
        return new DlqEvent(
                id,
                topic,
                consumerGroup,
                offset,
                envelope,
                reason,
                attempts,
                failedAt,
                DlqEventStatus.REPLAY_FAILED,
                replayedAt,
                replayError
        );
    }
}