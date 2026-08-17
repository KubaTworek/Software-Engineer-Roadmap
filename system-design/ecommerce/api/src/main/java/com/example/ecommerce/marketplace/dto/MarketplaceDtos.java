package com.example.ecommerce.marketplace.dto;

import com.example.ecommerce.marketplace.SellerStatus;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public final class MarketplaceDtos {
    private MarketplaceDtos() {}

    public record CreateSellerRequest(
            @NotBlank String displayName,
            @NotBlank String slug
    ) {}

    public record SellerResponse(
            Long id,
            Long ownerId,
            String ownerEmail,
            String displayName,
            String slug,
            SellerStatus status,
            BigDecimal commissionRate
    ) {}
}
