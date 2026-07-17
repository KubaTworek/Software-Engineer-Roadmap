package com.example.ecommerce.loyalty.dto;

public final class LoyaltyDtos {
    private LoyaltyDtos() {}

    public record LoyaltyAccountResponse(
            Long accountId,
            Long userId,
            int pointsBalance,
            String tier
    ) {}

    public record RedeemRequest(int points, Long orderId) {}
}
