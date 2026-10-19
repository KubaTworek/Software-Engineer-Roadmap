package com.example.newsfeed.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventTest {
    @Test
    void expiredClaimCanReturnToDueSetWithoutPretendingPublicationSucceeded() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        DomainEvent event = new DomainEvent(UUID.randomUUID(), "post.created", UUID.randomUUID(), "{}", now);

        event.claim(now, Duration.ofSeconds(30));

        assertThat(event.getStatus()).isEqualTo(DomainEventStatus.CLAIMED);
        assertThat(event.getNextAttemptAt()).isEqualTo(now.plusSeconds(30));
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void failureSchedulesBoundedExponentialRetry() {
        DomainEvent event = new DomainEvent(UUID.randomUUID(), "post.created", UUID.randomUUID(), "{}", Instant.now());
        event.markFailed(new RuntimeException("broker unavailable"));

        assertThat(event.getStatus()).isEqualTo(DomainEventStatus.FAILED);
        assertThat(event.getAttempts()).isOne();
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
    }
}
