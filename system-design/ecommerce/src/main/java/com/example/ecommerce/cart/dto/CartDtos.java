package com.example.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {
    private CartDtos() {}

    public record AddCartItemRequest(
            @NotNull Long productVariantId,
            @NotNull @Min(1) Integer quantity
    ) {}

    public record UpdateCartItemRequest(
            @NotNull @Min(1) Integer quantity
    ) {}

    public record CartResponse(
            Long id,
            List<CartItemResponse> items,
            BigDecimal subtotal,
            String currency
    ) {}

    public record CartItemResponse(
            Long id,
            Long productId,
            Long productVariantId,
            String productName,
            String variantName,
            String sku,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String currency
    ) {}
}
