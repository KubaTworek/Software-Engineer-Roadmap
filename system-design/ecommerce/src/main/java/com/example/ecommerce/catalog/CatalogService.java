package com.example.ecommerce.catalog;

import com.example.ecommerce.catalog.dto.CatalogDtos;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.inventory.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CategoryRepository categories;
    private final InventoryService inventory;

    public CatalogService(
            ProductRepository products,
            ProductVariantRepository variants,
            CategoryRepository categories,
            InventoryService inventory
    ) {
        this.products = products;
        this.variants = variants;
        this.categories = categories;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.CategoryResponse> categories() {
        return categories.findAll().stream().map(this::toCategoryResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductResponse> activeProducts() {
        return products.findByStatus(ProductStatus.ACTIVE).stream().map(this::toProductResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return activeProducts();
        }
        return products
                .findByStatusAndNameContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCase(
                        ProductStatus.ACTIVE,
                        query,
                        ProductStatus.ACTIVE,
                        query
                )
                .stream()
                .map(this::toProductResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogDtos.ProductResponse bySlug(String slug) {
        Product product = products.findBySlugAndStatus(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        return toProductResponse(product);
    }

    public ProductVariant getActiveVariant(Long variantId) {
        ProductVariant variant = variants.findById(variantId)
                .orElseThrow(() -> ApiException.notFound("Product variant not found"));

        if (!variant.isActive() || variant.getProduct().getStatus() != ProductStatus.ACTIVE) {
            throw ApiException.badRequest("Product variant is not active");
        }

        return variant;
    }

    @Transactional
    public CatalogDtos.ProductResponse createProduct(CatalogDtos.CreateProductRequest request) {
        Category category = categories.findById(request.categoryId())
                .orElseThrow(() -> ApiException.notFound("Category not found"));

        Product product = new Product(
                request.sku(),
                request.name(),
                request.slug(),
                request.description(),
                request.brand(),
                category
        );

        ProductVariant variant = new ProductVariant(
                request.variantSku(),
                request.variantName(),
                request.price(),
                request.currency()
        );

        product.addVariant(variant);
        products.save(product);
        inventory.createInventoryItem(variant, request.initialStock());

        return toProductResponse(product);
    }

    public CatalogDtos.ProductResponse toProductResponse(Product product) {
        return new CatalogDtos.ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getBrand(),
                toCategoryResponse(product.getCategory()),
                product.getStatus(),
                product.getVariants().stream().map(this::toVariantResponse).toList()
        );
    }

    private CatalogDtos.CategoryResponse toCategoryResponse(Category category) {
        return new CatalogDtos.CategoryResponse(category.getId(), category.getParentId(), category.getName(), category.getSlug());
    }

    private CatalogDtos.ProductVariantResponse toVariantResponse(ProductVariant variant) {
        return new CatalogDtos.ProductVariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getPrice(),
                variant.getCurrency(),
                variant.isActive(),
                inventory.availableQuantity(variant.getId())
        );
    }
}
