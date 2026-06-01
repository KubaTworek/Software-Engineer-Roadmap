package pl.jakubtworek.marketplace.integration.kafka;

import java.time.Instant;
import java.util.UUID;

public record DlqEvent(
        UUID id,
        String originalTopic,
        String consumerGroup,
        long originalOffset,
        IntegrationEventEnvelope envelope,
        String reason,
        int attempts,
        DlqEventStatus status,
        Instant failedAt,
        Instant replayedAt
) {
    public static DlqEvent newEvent(String topic, String consumerGroup, long offset, IntegrationEventEnvelope envelope, String reason, int attempts) {
        return new DlqEvent(UUID.randomUUID(), topic, consumerGroup, offset, envelope, reason, attempts, DlqEventStatus.NEW, Instant.now(), null);
    }

    public DlqEvent markReplayed() {
        return new DlqEvent(id, originalTopic, consumerGroup, originalOffset, envelope, reason, attempts, DlqEventStatus.REPLAYED, failedAt, Instant.now());
    }

    public DlqEvent markReplayFailed(String reason) {
        return new DlqEvent(id, originalTopic, consumerGroup, originalOffset, envelope, reason, attempts, DlqEventStatus.REPLAY_FAILED, failedAt, replayedAt);
    }
}
