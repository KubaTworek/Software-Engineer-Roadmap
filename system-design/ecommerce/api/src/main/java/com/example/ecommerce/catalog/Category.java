package com.example.ecommerce.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "categories", indexes = {
        @Index(name = "idx_categories_slug", columnList = "slug", unique = true)
})
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long parentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    protected Category() {}

    public Category(String name, String slug, Long parentId) {
        this.name = name;
        this.slug = slug;
        this.parentId = parentId;
    }

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
}
