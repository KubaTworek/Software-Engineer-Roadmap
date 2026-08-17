package com.example.ecommerce.pricing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "dynamic_price_rules")
public class DynamicPriceRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private Long variantId;
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DynamicPriceRuleType type;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal multiplier = BigDecimal.ONE;

    @Column(precision = 12, scale = 2)
    private BigDecimal fixedPrice;

    private Instant startsAt;
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active = true;

    protected DynamicPriceRule() {}

    public DynamicPriceRule(Long productId, Long variantId, Long categoryId, DynamicPriceRuleType type, BigDecimal multiplier, BigDecimal fixedPrice, Instant startsAt, Instant endsAt) {
        this.productId = productId;
        this.variantId = variantId;
        this.categoryId = categoryId;
        this.type = type;
        this.multiplier = multiplier == null ? BigDecimal.ONE : multiplier;
        this.fixedPrice = fixedPrice;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getVariantId() { return variantId; }
    public Long getCategoryId() { return categoryId; }
    public DynamicPriceRuleType getType() { return type; }
    public BigDecimal getMultiplier() { return multiplier; }
    public BigDecimal getFixedPrice() { return fixedPrice; }

    public boolean appliesTo(Long productId, Long variantId, Long categoryId, Instant now) {
        return active
                && (startsAt == null || !startsAt.isAfter(now))
                && (endsAt == null || !endsAt.isBefore(now))
                && (this.productId == null || this.productId.equals(productId))
                && (this.variantId == null || this.variantId.equals(variantId))
                && (this.categoryId == null || this.categoryId.equals(categoryId));
    }
}
