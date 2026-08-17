package com.example.ecommerce.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {
    List<WarehouseStock> findByVariantIdOrderByAvailableQuantityDesc(Long variantId);
    Optional<WarehouseStock> findByWarehouseIdAndVariantId(Long warehouseId, Long variantId);
}
