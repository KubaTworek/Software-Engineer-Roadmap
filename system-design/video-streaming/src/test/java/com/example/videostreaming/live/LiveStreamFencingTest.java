package com.example.videostreaming.live;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class LiveStreamFencingTest {
    @Test
    void newerOwnerPreventsExpiredWorkerFromPublishingState() {
        LiveStream stream = new LiveStream("live", "demo", null, LiveLatencyMode.STANDARD,
                "secret", "rtmp://public", "rtmp://internal", true, 600, true);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        long oldOwner = stream.claim(start, Duration.ofSeconds(10));
        long newOwner = stream.claim(start.plusSeconds(11), Duration.ofMinutes(1));

        assertThatThrownBy(() -> stream.markLive(oldOwner, start.plusSeconds(12), "old.m3u8", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fencing");

        stream.markLive(newOwner, start.plusSeconds(12), "new.m3u8", null);
        assertThat(stream.getStatus()).isEqualTo(LiveStatus.LIVE);
        assertThat(stream.getHlsMasterObjectKey()).isEqualTo("new.m3u8");
    }

    @Test
    void heartbeatExtendsLeaseButStaleOwnerCannotRenewIt() {
        LiveStream stream = new LiveStream("live", "demo", null, LiveLatencyMode.STANDARD,
                "secret", "rtmp://public", "rtmp://internal", true, 600, true);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        long token = stream.claim(start, Duration.ofSeconds(10));

        stream.renew(token, start.plusSeconds(9), Duration.ofSeconds(10));

        assertThat(stream.ownedBy(token, start.plusSeconds(18))).isTrue();
        assertThatThrownBy(() -> stream.renew(token + 1, start.plusSeconds(10), Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fencing");
    }
}
