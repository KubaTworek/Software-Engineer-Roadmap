package com.example.searchindexer.catalog;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {
    @Id
    private Long id;
    private String sku;
    private String name;
    private String slug;
    @Column(length = 4000)
    private String description;
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    @OneToMany(mappedBy = "product")
    private List<ProductVariant> variants = new ArrayList<>();

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getBrand() { return brand; }
    public Category getCategory() { return category; }
    public List<ProductVariant> getVariants() { return variants; }
}
