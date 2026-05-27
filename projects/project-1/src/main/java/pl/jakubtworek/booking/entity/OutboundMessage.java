package pl.jakubtworek.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbound_messages")
public class OutboundMessage {
    @Id
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(nullable = false, length = 80)
    private String channel;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false, length = 1000)
    private String payload;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboundMessage() {
    }

    private OutboundMessage(UUID reservationId, String channel, String status, String payload, String errorMessage) {
        this.id = UUID.randomUUID();
        this.reservationId = reservationId;
        this.channel = channel;
        this.status = status;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public static OutboundMessage sent(UUID reservationId, String channel, String payload) {
        return new OutboundMessage(reservationId, channel, "SENT", payload, null);
    }

    public static OutboundMessage failed(UUID reservationId, String channel, String payload, Throwable error) {
        String message = error == null ? "unknown" : error.getClass().getSimpleName() + ": " + error.getMessage();
        return new OutboundMessage(reservationId, channel, "FAILED", payload, message);
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public String getChannel() { return channel; }
    public String getStatus() { return status; }
    public String getPayload() { return payload; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
