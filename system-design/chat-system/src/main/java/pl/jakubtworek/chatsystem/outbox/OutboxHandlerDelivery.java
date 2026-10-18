package pl.jakubtworek.chatsystem.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Trwały inbox pojedynczego handlera; retry eventu nie powtarza już zakończonych konsumentów. */
@Entity
@Table(name = "outbox_handler_deliveries", uniqueConstraints = @UniqueConstraint(
        name = "uk_outbox_handler_delivery", columnNames = {"event_id", "handler_name"}))
public class OutboxHandlerDelivery {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "handler_name", nullable = false, length = 240) private String handlerName;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;

    protected OutboxHandlerDelivery() {}
    public OutboxHandlerDelivery(UUID eventId, String handlerName) {
        this.eventId = eventId; this.handlerName = handlerName; this.completedAt = Instant.now();
    }
}
