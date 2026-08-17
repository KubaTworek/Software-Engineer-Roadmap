package com.example.ecommerce.pricing.dto;

import com.example.ecommerce.pricing.DynamicPriceRuleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class PricingDtos {
    private PricingDtos() {}

    public record DynamicPriceRequest(
            @NotNull Long productId,
            @NotNull Long variantId,
            Long categoryId,
            @NotNull @DecimalMin("0.01") BigDecimal basePrice
    ) {}

    public record DynamicPriceResponse(
            Long productId,
            Long variantId,
            BigDecimal basePrice,
            BigDecimal finalPrice,
            String reason
    ) {}

    public record CreateDynamicPriceRuleRequest(
            Long productId,
            Long variantId,
            Long categoryId,
            @NotNull DynamicPriceRuleType type,
            BigDecimal multiplier,
            BigDecimal fixedPrice,
            Instant startsAt,
            Instant endsAt
    ) {}
}
