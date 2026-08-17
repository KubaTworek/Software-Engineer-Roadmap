package com.example.ecommerce.promotion.dto;

import com.example.ecommerce.promotion.PromotionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PromotionDtos {
    private PromotionDtos() {}

    public record CreatePromotionRequest(
            @NotBlank String name,
            @NotNull PromotionType type,
            @NotNull @DecimalMin("0.01") BigDecimal value,
            Instant startsAt,
            Instant endsAt,
            Long categoryId,
            Long productId,
            Integer buyQuantity,
            Integer freeQuantity,
            Integer priority,
            Boolean stackable
    ) {}

    public record CreateCouponRequest(
            @NotBlank String code,
            @NotNull Long promotionId,
            Integer maxUses,
            Integer userLimit
    ) {}

    public record PriceLine(
            Long productId,
            Long productVariantId,
            Long categoryId,
            int quantity,
            BigDecimal unitPrice
    ) {}

    public record PriceRequest(
            List<PriceLine> lines,
            String couponCode,
            BigDecimal shippingAmount
    ) {}

    public record PromotionAdjustment(
            String promotionName,
            BigDecimal amount,
            String reason
    ) {}

    public record PriceResponse(
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal shippingAmount,
            BigDecimal total,
            List<PromotionAdjustment> adjustments
    ) {}
}
