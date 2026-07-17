package com.example.ecommerce.catalog;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_slug", columnList = "slug", unique = true),
        @Index(name = "idx_products_status", columnList = "status")
})
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(length = 4000)
    private String description;

    private String brand;

    @ManyToOne(optional = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() {}

    public Product(String sku, String name, String slug, String description, String brand, Category category) {
        this.sku = sku;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.brand = brand;
        this.category = category;
    }

    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        variant.setProduct(this);
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getBrand() { return brand; }
    public Category getCategory() { return category; }
    public ProductStatus getStatus() { return status; }
    public List<ProductVariant> getVariants() { return variants; }

    public void update(String name, String description, String brand, ProductStatus status) {
        this.name = name;
        this.description = description;
        this.brand = brand;
        this.status = status;
    }
}
