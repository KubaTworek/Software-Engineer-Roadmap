package com.example.ecommerce.promotion;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "promotions", indexes = {
        @Index(name = "idx_promotions_status_dates", columnList = "status,startsAt,endsAt")
})
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PromotionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PromotionStatus status = PromotionStatus.ACTIVE;

    private Instant startsAt;
    private Instant endsAt;

    @Column(nullable = false)
    private int priority = 100;

    @Column(precision = 12, scale = 2)
    private BigDecimal value;

    private Long categoryId;
    private Long productId;

    private Integer buyQuantity;
    private Integer freeQuantity;

    @Column(nullable = false)
    private boolean stackable = false;

    protected Promotion() {}

    public Promotion(String name, PromotionType type, BigDecimal value, Instant startsAt, Instant endsAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public PromotionType getType() { return type; }
    public PromotionStatus getStatus() { return status; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getPriority() { return priority; }
    public BigDecimal getValue() { return value; }
    public Long getCategoryId() { return categoryId; }
    public Long getProductId() { return productId; }
    public Integer getBuyQuantity() { return buyQuantity; }
    public Integer getFreeQuantity() { return freeQuantity; }
    public boolean isStackable() { return stackable; }

    public boolean isActiveAt(Instant now) {
        return status == PromotionStatus.ACTIVE
                && (startsAt == null || !startsAt.isAfter(now))
                && (endsAt == null || !endsAt.isBefore(now));
    }

    public void setTargetCategory(Long categoryId) { this.categoryId = categoryId; }
    public void setTargetProduct(Long productId) { this.productId = productId; }
    public void setBuyXGetY(Integer buyQuantity, Integer freeQuantity) {
        this.buyQuantity = buyQuantity;
        this.freeQuantity = freeQuantity;
    }
    public void setPriority(int priority) { this.priority = priority; }
    public void setStackable(boolean stackable) { this.stackable = stackable; }
}
