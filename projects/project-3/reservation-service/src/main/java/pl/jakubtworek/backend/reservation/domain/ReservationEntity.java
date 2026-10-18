package pl.jakubtworek.backend.reservation.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class ReservationEntity {
    @Id
    private UUID id;
    private UUID eventId;
    private String userId;
    private int quantity;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected ReservationEntity() {
    }

    public ReservationEntity(UUID id, UUID eventId, String userId, int quantity, ReservationStatus status, Instant expiresAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.quantity = quantity;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ReservationEntity pending(UUID eventId, String userId, int quantity, Instant now) {
        return new ReservationEntity(UUID.randomUUID(), eventId, userId, quantity, ReservationStatus.PENDING, now.plusSeconds(15 * 60), now, now);
    }

    public void confirm(Instant now) {
        if (this.status == ReservationStatus.EXPIRED || this.status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Cannot confirm reservation in status " + this.status);
        }
        this.status = ReservationStatus.CONFIRMED;
        this.updatedAt = now;
    }

    public boolean cancel(Instant now) {
        if (this.status == ReservationStatus.CANCELLED || this.status == ReservationStatus.EXPIRED) {
            return false;
        }
        if (this.status == ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel already confirmed reservation");
        }
        this.status = ReservationStatus.CANCELLED;
        this.updatedAt = now;
        return true;
    }

    public boolean expire(Instant now) {
        if (this.status == ReservationStatus.PENDING) {
            this.status = ReservationStatus.EXPIRED;
            this.updatedAt = now;
            return true;
        }
        return false;
    }

    public boolean isExpired(Instant now) {
        return status == ReservationStatus.PENDING && expiresAt.isBefore(now);
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getUserId() { return userId; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
