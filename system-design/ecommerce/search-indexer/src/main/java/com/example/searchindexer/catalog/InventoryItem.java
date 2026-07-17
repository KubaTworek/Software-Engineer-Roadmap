package com.example.searchindexer.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {
    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    private int availableQuantity;
    private int reservedQuantity;

    public ProductVariant getVariant() { return variant; }
    public int sellableQuantity() { return availableQuantity - reservedQuantity; }
}
