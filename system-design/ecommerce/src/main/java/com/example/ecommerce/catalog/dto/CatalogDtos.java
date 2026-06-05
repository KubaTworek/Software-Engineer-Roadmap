package com.example.ecommerce.catalog.dto;

import com.example.ecommerce.catalog.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CategoryResponse(Long id, Long parentId, String name, String slug) {}

    public record ProductResponse(
            Long id,
            String sku,
            String name,
            String slug,
            String description,
            String brand,
            CategoryResponse category,
            ProductStatus status,
            List<ProductVariantResponse> variants
    ) {}

    public record ProductVariantResponse(
            Long id,
            String sku,
            String name,
            BigDecimal price,
            String currency,
            boolean active,
            int availableQuantity
    ) {}

    public record CreateProductRequest(
            @NotBlank String sku,
            @NotBlank String name,
            @NotBlank String slug,
            @NotBlank String description,
            @NotBlank String brand,
            @NotNull Long categoryId,
            @NotBlank String variantSku,
            @NotBlank String variantName,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @NotBlank String currency,
            @NotNull Integer initialStock
    ) {}
}
