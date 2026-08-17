package com.example.searchindexer.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;
    private String sku;
    private String name;
    @Column(precision = 12, scale = 2)
    private BigDecimal price;
    private String currency;
    private boolean active;

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public boolean isActive() { return active; }
}
