package pl.jakubtworek.cloudarchitecture.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity mapped to the orders table.
 *
 * Orders are durable business records and must be stored in the database,
 * not in memory of a single application instance.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_customer_created_at", columnList = "customer_id,created_at")
})
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Customer identifier used for filtering and reporting. */
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    /** Creation timestamp helps with ordering, reporting, and time-range queries. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderEntity() {
        // Required by JPA.
    }

    public OrderEntity(String customerId, Instant createdAt) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        this.customerId = customerId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public Instant getCreatedAt() { return createdAt; }
}
