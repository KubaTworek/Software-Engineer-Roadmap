package com.example.videostreaming.premium;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class PremiumDtos {
    private PremiumDtos() {}

    public record SubscriptionResponse(UUID id, UUID userId, SubscriptionPlanCode planCode, SubscriptionStatus status,
                                       Instant startedAt, Instant expiresAt) {
        public static SubscriptionResponse from(UserSubscription subscription) {
            return new SubscriptionResponse(subscription.getId(), subscription.getUser().getId(), subscription.getPlanCode(),
                    subscription.getStatus(), subscription.getStartedAt(), subscription.getExpiresAt());
        }
    }

    public record CreateSubscriptionRequest(@NotNull UUID userId, @NotNull SubscriptionPlanCode planCode, Instant expiresAt) {}

    public record EntitlementResponse(UUID userId, UUID videoId, boolean allowed, String reason,
                                      SubscriptionPlanCode userPlan, SubscriptionPlanCode requiredPlan,
                                      boolean drmRequired, boolean geoAllowed) {}

    public record UpdateVideoPremiumPolicyRequest(SubscriptionPlanCode minimumPlanCode, String allowedCountries,
                                                  Boolean drmProtected, String licensePolicy) {}
}
