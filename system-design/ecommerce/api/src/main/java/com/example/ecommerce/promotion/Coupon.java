package com.example.ecommerce.promotion;

import jakarta.persistence.*;

@Entity
@Table(name = "coupons", indexes = {
        @Index(name = "idx_coupons_code", columnList = "code", unique = true)
})
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @ManyToOne(optional = false)
    private Promotion promotion;

    private Integer maxUses;
    private Integer usedCount = 0;
    private Integer userLimit;

    @Column(nullable = false)
    private boolean active = true;

    protected Coupon() {}

    public Coupon(String code, Promotion promotion, Integer maxUses, Integer userLimit) {
        this.code = code.toUpperCase();
        this.promotion = promotion;
        this.maxUses = maxUses;
        this.userLimit = userLimit;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Promotion getPromotion() { return promotion; }
    public Integer getMaxUses() { return maxUses; }
    public Integer getUsedCount() { return usedCount; }
    public Integer getUserLimit() { return userLimit; }
    public boolean isActive() { return active; }

    public boolean canUse() {
        return active && (maxUses == null || usedCount < maxUses);
    }

    public void markUsed() {
        this.usedCount = usedCount + 1;
    }
}
