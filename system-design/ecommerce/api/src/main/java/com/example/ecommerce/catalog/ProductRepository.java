package com.example.ecommerce.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

    List<Product> findByStatus(ProductStatus status);

    List<Product> findByStatusAndNameContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCase(
            ProductStatus status1,
            String nameQuery,
            ProductStatus status2,
            String descriptionQuery
    );
}
