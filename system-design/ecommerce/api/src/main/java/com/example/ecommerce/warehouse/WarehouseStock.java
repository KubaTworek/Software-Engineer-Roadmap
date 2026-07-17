package com.example.ecommerce.warehouse;

import com.example.ecommerce.catalog.ProductVariant;
import jakarta.persistence.*;

@Entity
@Table(name = "warehouse_stock", indexes = {
        @Index(name = "idx_warehouse_stock_variant", columnList = "variant_id"),
        @Index(name = "idx_warehouse_stock_warehouse_variant", columnList = "warehouse_id,variant_id", unique = true)
})
public class WarehouseStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(optional = false)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(nullable = false)
    private int availableQuantity;

    @Column(nullable = false)
    private int reservedQuantity;

    protected WarehouseStock() {}

    public WarehouseStock(Warehouse warehouse, ProductVariant variant, int availableQuantity) {
        this.warehouse = warehouse;
        this.variant = variant;
        this.availableQuantity = availableQuantity;
    }

    public Long getId() { return id; }
    public Warehouse getWarehouse() { return warehouse; }
    public ProductVariant getVariant() { return variant; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public int sellableQuantity() { return availableQuantity - reservedQuantity; }

    public void reserve(int quantity) { this.reservedQuantity += quantity; }
    public void release(int quantity) { this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity); }
    public void confirmSale(int quantity) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
        this.availableQuantity = Math.max(0, this.availableQuantity - quantity);
    }
    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = Math.max(0, availableQuantity); }
}
