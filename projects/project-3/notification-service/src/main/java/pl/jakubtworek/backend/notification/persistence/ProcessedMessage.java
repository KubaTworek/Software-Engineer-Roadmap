package pl.jakubtworek.backend.notification.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbox marker zamienia at-least-once delivery w exactly-once efekt lokalny.
 * Nie twierdzi, że broker dostarcza exactly once; unikalny klucz jedynie sprawia,
 * że ponowne dostarczenie tego samego eventu nie powtarza efektu biznesowego.
 */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {
    @Id private UUID messageId;
    private Instant processedAt;

    protected ProcessedMessage() { }
    public ProcessedMessage(UUID messageId, Instant processedAt) {
        this.messageId = messageId;
        this.processedAt = processedAt;
    }
}
