package com.example.ecommerce.marketplace;

import com.example.ecommerce.auth.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "marketplace_sellers", indexes = {
        @Index(name = "idx_sellers_slug", columnList = "slug", unique = true)
})
public class Seller {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private AppUser owner;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status = SellerStatus.PENDING_VERIFICATION;

    @Column(nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal commissionRate = java.math.BigDecimal.valueOf(10.00);

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Seller() {}

    public Seller(AppUser owner, String displayName, String slug) {
        this.owner = owner;
        this.displayName = displayName;
        this.slug = slug;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getDisplayName() { return displayName; }
    public String getSlug() { return slug; }
    public SellerStatus getStatus() { return status; }
    public java.math.BigDecimal getCommissionRate() { return commissionRate; }

    public void activate() { this.status = SellerStatus.ACTIVE; }
    public void suspend() { this.status = SellerStatus.SUSPENDED; }
}
