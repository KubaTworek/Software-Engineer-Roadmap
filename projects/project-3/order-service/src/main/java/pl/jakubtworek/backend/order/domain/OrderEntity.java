package pl.jakubtworek.backend.order.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    private UUID id;
    private UUID reservationId;
    private String userId;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private String idempotencyKey;
    private Instant createdAt;
    private String degradationReason;

    protected OrderEntity() {
    }

    public OrderEntity(UUID id, UUID reservationId, String userId, BigDecimal amount, OrderStatus status, String idempotencyKey, Instant createdAt, String degradationReason) {
        this.id = id;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.degradationReason = degradationReason;
    }

    public static OrderEntity pending(UUID reservationId, String userId, String idempotencyKey) {
        return new OrderEntity(UUID.randomUUID(), reservationId, userId, new BigDecimal("199.00"), OrderStatus.PENDING, idempotencyKey, Instant.now(), null);
    }

    public void markPaid() {
        this.status = OrderStatus.PAID;
        this.degradationReason = null;
    }

    public void markPaymentPending(String reason) {
        this.status = OrderStatus.PAYMENT_PENDING;
        this.degradationReason = reason;
    }

    public void markFailed(String reason) {
        this.status = OrderStatus.FAILED;
        this.degradationReason = reason;
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public OrderStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDegradationReason() { return degradationReason; }
}
