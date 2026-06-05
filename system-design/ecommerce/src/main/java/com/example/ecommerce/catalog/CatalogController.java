package com.example.ecommerce.catalog;

import com.example.ecommerce.catalog.dto.CatalogDtos;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/categories")
    public List<CatalogDtos.CategoryResponse> categories() {
        return catalog.categories();
    }

    @GetMapping("/api/products")
    public List<CatalogDtos.ProductResponse> products() {
        return catalog.activeProducts();
    }

    @GetMapping("/api/products/{slug}")
    public CatalogDtos.ProductResponse product(@PathVariable String slug) {
        return catalog.bySlug(slug);
    }

    @GetMapping("/api/search")
    public List<CatalogDtos.ProductResponse> search(@RequestParam(required = false) String q) {
        return catalog.search(q);
    }
}
