package com.example.ecommerce.inventory;

import com.example.ecommerce.catalog.ProductVariant;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_items", indexes = {
        @Index(name = "idx_inventory_variant", columnList = "variant_id", unique = true)
})
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(nullable = false)
    private int availableQuantity;

    @Column(nullable = false)
    private int reservedQuantity;

    @Column(nullable = false)
    private int soldQuantity;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected InventoryItem() {}

    public InventoryItem(ProductVariant variant, int availableQuantity) {
        this.variant = variant;
        this.availableQuantity = availableQuantity;
    }

    public Long getId() { return id; }
    public ProductVariant getVariant() { return variant; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public int getSoldQuantity() { return soldQuantity; }

    public int sellableQuantity() {
        return availableQuantity - reservedQuantity;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.reservedQuantity += quantity;
        this.updatedAt = Instant.now();
    }

    public void release(int quantity) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
        this.updatedAt = Instant.now();
    }

    public void confirmSale(int quantity) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
        this.availableQuantity = Math.max(0, this.availableQuantity - quantity);
        this.soldQuantity += quantity;
        this.updatedAt = Instant.now();
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
        this.updatedAt = Instant.now();
    }
}
