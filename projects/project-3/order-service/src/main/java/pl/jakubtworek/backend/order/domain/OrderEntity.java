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

    protected OrderEntity() {
    }

    public OrderEntity(UUID id, UUID reservationId, String userId, BigDecimal amount, OrderStatus status, String idempotencyKey, Instant createdAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public static OrderEntity pending(UUID reservationId, String userId, String idempotencyKey) {
        return new OrderEntity(UUID.randomUUID(), reservationId, userId, new BigDecimal("199.00"), OrderStatus.PENDING, idempotencyKey, Instant.now());
    }

    public void markPaid() { this.status = OrderStatus.PAID; }
    public void markPaymentPending() { this.status = OrderStatus.PAYMENT_PENDING; }
    public void markFailed() { this.status = OrderStatus.FAILED; }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public OrderStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
