package com.example.notification;

import com.example.notification.channel.ChannelSemantics;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ChannelSemanticsTest {
    @Test void emailReputationCanThrottleOrPauseTraffic() {
        assertThat(ChannelSemantics.emailDecision(10_000, 100, 0, true))
                .isEqualTo(ChannelSemantics.TrafficDecision.ALLOW);
        assertThat(ChannelSemantics.emailDecision(10_000, 250, 0, true))
                .isEqualTo(ChannelSemantics.TrafficDecision.THROTTLE);
        assertThat(ChannelSemantics.emailDecision(10_000, 10, 10, true))
                .isEqualTo(ChannelSemantics.TrafficDecision.PAUSE);
    }

    @Test void smsCostDependsOnAlphabetAndConcatenation() {
        assertThat(ChannelSemantics.smsEncoding("a".repeat(161)).segments()).isEqualTo(2);
        assertThat(ChannelSemantics.smsEncoding("ą".repeat(71)).segments()).isEqualTo(2);
        assertThat(ChannelSemantics.smsEncoding("ą").alphabet()).isEqualTo("UCS-2");
    }

    @Test void invalidOldPushTokenCannotDeleteRotatedToken() {
        var registry = new ChannelSemantics.PushTokenRegistry();
        registry.register("t", "u", "phone", "old");
        registry.register("t", "u", "phone", "new");
        registry.invalidate("t", "u", "phone", "old");
        assertThat(registry.active("t", "u", "phone")).contains("new");
    }

    @Test void inAppUnreadProjectionIsIdempotent() {
        var projection = new ChannelSemantics.InAppUnreadProjection();
        UUID delivered = UUID.randomUUID(), read = UUID.randomUUID(), message = UUID.randomUUID();
        assertThat(projection.delivered(delivered, message)).isEqualTo(1);
        assertThat(projection.delivered(delivered, message)).isEqualTo(1);
        assertThat(projection.read(read, message)).isZero();
        assertThat(projection.read(read, message)).isZero();
    }
}
