package com.example.ecommerce.inventory;

import com.example.ecommerce.catalog.ProductVariant;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_reservation_order_variant",
                columnNames = {"order_id", "variant_id"}),
        indexes = @Index(name = "idx_inventory_reservation_order", columnList = "order_id"))
public class InventoryReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected InventoryReservation() {}

    public InventoryReservation(Long orderId, ProductVariant variant, int quantity, Instant expiresAt) {
        this.orderId = orderId;
        this.variant = variant;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public ProductVariant getVariant() { return variant; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }

    public void confirm() {
        requireActive("confirm");
        this.status = ReservationStatus.CONFIRMED;
    }

    public void release() {
        requireActive("release");
        this.status = ReservationStatus.RELEASED;
    }

    public void expire() {
        requireActive("expire");
        this.status = ReservationStatus.EXPIRED;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    private void requireActive(String operation) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot " + operation + " reservation in status " + status);
        }
    }
}
