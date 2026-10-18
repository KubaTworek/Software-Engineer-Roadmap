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
    private String requestFingerprint;
    private Instant createdAt;
    private String degradationReason;
    private int reconciliationAttempts;
    private Instant nextAttemptAt;

    protected OrderEntity() {
    }

    public OrderEntity(UUID id, UUID reservationId, String userId, BigDecimal amount, OrderStatus status,
                       String idempotencyKey, String requestFingerprint, Instant createdAt, String degradationReason) {
        this.id = id;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = createdAt;
        this.degradationReason = degradationReason;
    }

    public static OrderEntity pending(UUID reservationId, String userId, String idempotencyKey,
                                      String requestFingerprint, Instant now) {
        return new OrderEntity(UUID.randomUUID(), reservationId, userId, new BigDecimal("199.00"),
                OrderStatus.PENDING, idempotencyKey, requestFingerprint, now, null);
    }

    public void markPaymentInProgress() { this.status = OrderStatus.PAYMENT_IN_PROGRESS; }

    public void markPaid() {
        this.status = OrderStatus.PAID;
        this.degradationReason = null;
        this.nextAttemptAt = null;
    }

    public void markPaymentUnknown(String reason) {
        if (this.status != OrderStatus.PAYMENT_UNKNOWN) {
            this.reconciliationAttempts = 0;
            this.nextAttemptAt = null;
        }
        this.status = OrderStatus.PAYMENT_UNKNOWN;
        this.degradationReason = reason;
    }

    public void scheduleReconciliation(String reason, Instant now) {
        this.degradationReason = reason;
        this.reconciliationAttempts++;
        long delaySeconds = Math.min(300, 1L << Math.min(reconciliationAttempts, 9));
        this.nextAttemptAt = now.plusSeconds(delaySeconds);
    }

    public void markReservationConfirmationPending(String reason) {
        /* Każdy etap sagi ma własny retry budget. Próby lookupu płatności nie powinny
         * zużyć budżetu potwierdzania rezerwacji. */
        if (this.status != OrderStatus.RESERVATION_CONFIRMATION_PENDING) {
            this.reconciliationAttempts = 0;
            this.nextAttemptAt = null;
        }
        this.status = OrderStatus.RESERVATION_CONFIRMATION_PENDING;
        this.degradationReason = reason;
    }

    /**
     * Po ograniczonej liczbie prób potwierdzenia rezerwacji przestajemy liczyć na
     * samoistne uzdrowienie workflow. Płatność jest już znana jako wykonana, dlatego
     * bezpiecznym kolejnym krokiem jest kompensacja, a nie utworzenie kolejnej płatności.
     */
    public void markCompensationPending(String reason, Instant now) {
        this.status = OrderStatus.COMPENSATION_PENDING;
        this.degradationReason = reason;
        this.nextAttemptAt = now;
    }

    /** Nieznanego wyniku finansowego nie wolno automatycznie zgadywać w nieskończoność. */
    public void markManualReview(String reason) {
        this.status = OrderStatus.MANUAL_REVIEW;
        this.degradationReason = reason;
        this.nextAttemptAt = null;
    }

    public void markFailed(String reason) {
        this.status = OrderStatus.FAILED;
        this.degradationReason = reason;
        this.nextAttemptAt = null;
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public OrderStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDegradationReason() { return degradationReason; }
    public int getReconciliationAttempts() { return reconciliationAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
