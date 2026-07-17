package com.example.ecommerce.order.dto;

import com.example.ecommerce.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record OrderResponse(
            Long id,
            String orderNumber,
            OrderStatus status,
            BigDecimal subtotalAmount,
            BigDecimal shippingAmount,
            BigDecimal totalAmount,
            String currency,
            String shippingAddress,
            String billingAddress,
            String shippingMethod,
            Instant createdAt,
            List<OrderItemResponse> items
    ) {}

    public record OrderItemResponse(
            Long id,
            Long productId,
            Long productVariantId,
            String sku,
            String productName,
            String variantName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}
}
