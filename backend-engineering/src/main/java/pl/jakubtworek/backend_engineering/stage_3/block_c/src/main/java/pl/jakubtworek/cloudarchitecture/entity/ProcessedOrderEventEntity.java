package pl.jakubtworek.cloudarchitecture.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** Durable deduplication marker for an order-created message. */
@Entity
@Table(name = "processed_order_events")
public class ProcessedOrderEventEntity {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedOrderEventEntity() {
        // Required by JPA.
    }

    public ProcessedOrderEventEntity(Long orderId, Instant processedAt) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        this.orderId = orderId;
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt must not be null");
    }

    public Long getOrderId() { return orderId; }
    public Instant getProcessedAt() { return processedAt; }
}
