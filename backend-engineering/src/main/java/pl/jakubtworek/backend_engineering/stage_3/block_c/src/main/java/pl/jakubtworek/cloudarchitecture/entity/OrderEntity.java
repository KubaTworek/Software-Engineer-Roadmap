package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity mapped to the orders table.
 *
 * Orders are durable business records and must be stored in the database,
 * not in memory of a single application instance.
 */
@Entity
@Table(name = "orders", indexes = {@Index(name = "idx_orders_customer_created_at", columnList = "customerId,createdAt")})
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Customer identifier used for filtering and reporting. */
    @Column(nullable = false)
    private String customerId;

    /** Creation timestamp helps with ordering, reporting, and time-range queries. */
    @Column(nullable = false)
    private Instant createdAt;

    protected OrderEntity() {
        // Required by JPA.
    }

    public OrderEntity(String customerId) {
        this.customerId = customerId;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public Instant getCreatedAt() { return createdAt; }
}
