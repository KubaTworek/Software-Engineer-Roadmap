package com.example.ecommerce.catalog;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants", indexes = {
        @Index(name = "idx_product_variants_sku", columnList = "sku", unique = true)
})
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Product product;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "PLN";

    @Column(nullable = false)
    private boolean active = true;

    protected ProductVariant() {}

    public ProductVariant(String sku, String name, BigDecimal price, String currency) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.currency = currency;
    }

    void setProduct(Product product) {
        this.product = product;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public boolean isActive() { return active; }
}
