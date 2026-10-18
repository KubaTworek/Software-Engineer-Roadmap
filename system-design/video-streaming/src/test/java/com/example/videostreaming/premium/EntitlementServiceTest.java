package com.example.videostreaming.premium;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.auth.UserRole;
import com.example.videostreaming.catalog.Video;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntitlementServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void geoRestrictionWinsEvenForPremiumSubscriber() {
        User user = new User("viewer@example.test", "hash", UserRole.USER);
        Video video = new Video("Premium", "demo", user);
        video.updatePremiumPolicy(SubscriptionPlanCode.PREMIUM, "PL", true, "STREAMING_ONLY");
        UserSubscriptionRepository repository = mock(UserSubscriptionRepository.class);
        when(repository.findBestActiveForUser(user.getId(), NOW)).thenReturn(Optional.of(
                new UserSubscription(user, SubscriptionPlanCode.PREMIUM, NOW.plusSeconds(3600))));

        var decision = new EntitlementService(repository, Clock.fixed(NOW, ZoneOffset.UTC))
                .check(user, video, false);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.geoAllowed()).isFalse();
        assertThat(decision.reason()).contains("region");
    }

    @Test
    void planHierarchyAllowsPremiumToWatchBasicContent() {
        User user = new User("viewer@example.test", "hash", UserRole.USER);
        Video video = new Video("Basic", "demo", user);
        video.updatePremiumPolicy(SubscriptionPlanCode.BASIC, null, false, null);
        UserSubscriptionRepository repository = mock(UserSubscriptionRepository.class);
        when(repository.findBestActiveForUser(user.getId(), NOW)).thenReturn(Optional.of(
                new UserSubscription(user, SubscriptionPlanCode.PREMIUM, NOW.plusSeconds(3600))));

        var decision = new EntitlementService(repository, Clock.fixed(NOW, ZoneOffset.UTC))
                .check(user, video, true);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.userPlan()).isEqualTo(SubscriptionPlanCode.PREMIUM);
    }
}
