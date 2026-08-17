package com.example.searchindexer.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    private Long id;
    private String name;
    private String slug;

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
}
