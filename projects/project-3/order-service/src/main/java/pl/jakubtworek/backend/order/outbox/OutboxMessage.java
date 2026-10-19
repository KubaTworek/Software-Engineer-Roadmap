package pl.jakubtworek.backend.order.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Trwała intencja publikacji zapisana w tej samej transakcji co OrderEntity.
 * Outbox nie daje exactly-once delivery: usuwa lukę DB→broker, a duplikaty nadal
 * muszą być tolerowane przez idempotentnego konsumenta.
 */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {
    @Id private UUID id;
    private String aggregateId;
    private String eventType;
    private String payload;
    private Instant occurredAt;
    private Instant publishedAt;
    private int publishAttempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant deadAt;

    protected OutboxMessage() { }

    public OutboxMessage(UUID id, String aggregateId, String eventType, String payload, Instant occurredAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public void markPublished(Instant now) { this.publishedAt = now; }
    public void scheduleRetry(Instant now, String error) {
        publishAttempts++;
        lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 1000));
        if (publishAttempts >= 10) {
            /* Poison payload lub trwały błąd routingu nie może blokować batcha bez końca.
             * Rekord pozostaje w bazie do inspekcji i kontrolowanego replayu. */
            deadAt = now;
            nextAttemptAt = null;
        } else {
            nextAttemptAt = now.plusSeconds(Math.min(300, 1L << Math.min(publishAttempts, 8)));
        }
    }
    public UUID getId() { return id; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getPublishAttempts() { return publishAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getDeadAt() { return deadAt; }
}
