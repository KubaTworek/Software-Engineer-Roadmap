package com.example.searchindexer.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    Optional<InventoryItem> findByVariantId(Long variantId);
}
