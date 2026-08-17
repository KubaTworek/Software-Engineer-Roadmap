package com.example.ecommerce.cart;

import com.example.ecommerce.catalog.ProductVariant;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Cart cart;

    @ManyToOne(optional = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant addedAt = Instant.now();

    protected CartItem() {}

    public CartItem(ProductVariant variant, int quantity, BigDecimal unitPriceSnapshot, String currency) {
        this.variant = variant;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.currency = currency;
    }

    public Long getId() { return id; }
    public Cart getCart() { return cart; }
    public ProductVariant getVariant() { return variant; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public String getCurrency() { return currency; }

    void setCart(Cart cart) {
        this.cart = cart;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal lineTotal() {
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }
}
