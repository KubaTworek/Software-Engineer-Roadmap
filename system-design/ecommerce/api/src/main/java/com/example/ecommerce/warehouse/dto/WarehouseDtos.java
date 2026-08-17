package com.example.ecommerce.warehouse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class WarehouseDtos {
    private WarehouseDtos() {}

    public record CreateWarehouseRequest(
            @NotBlank String code,
            @NotBlank String name,
            String country,
            String city,
            String address
    ) {}

    public record WarehouseResponse(
            Long id,
            String code,
            String name,
            String country,
            String city,
            String address
    ) {}

    public record SetWarehouseStockRequest(
            @NotNull Long warehouseId,
            @NotNull Long productVariantId,
            @NotNull @Min(0) Integer availableQuantity
    ) {}

    public record WarehouseStockResponse(
            Long warehouseId,
            String warehouseCode,
            Long productVariantId,
            int availableQuantity,
            int reservedQuantity,
            int sellableQuantity
    ) {}
}
