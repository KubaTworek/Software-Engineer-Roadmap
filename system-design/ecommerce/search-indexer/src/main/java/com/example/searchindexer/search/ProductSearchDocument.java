package com.example.searchindexer.search;

import java.math.BigDecimal;
import java.util.List;

public record ProductSearchDocument(
        Long productId,
        String sku,
        String name,
        String slug,
        String description,
        String brand,
        Long categoryId,
        String categoryName,
        List<VariantDocument> variants,
        boolean available
) {
    public record VariantDocument(
            Long variantId,
            String sku,
            String name,
            BigDecimal price,
            String currency,
            int availableQuantity
    ) {}
}
