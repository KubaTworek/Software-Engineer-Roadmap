package com.example.ecommerce.outbox;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventStateTest {

    @Test
    void repeatedFailuresEndInDeadLetterState() {
        OutboxEvent event = new OutboxEvent("Order", "1", "OrderCreated", "{}");

        for (int attempt = 0; attempt < 5; attempt++) {
            event.claim(Instant.parse("2030-01-01T00:00:00Z").plusSeconds(attempt));
            event.markFailed("broker unavailable");
        }

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(event.getPublishAttempts()).isEqualTo(5);
        assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    void staleProcessingClaimCanBeTakenAgainAfterCrashWindow() {
        OutboxEvent event = new OutboxEvent("Order", "1", "OrderCreated", "{}");
        event.claim(Instant.parse("2030-01-01T00:00:00Z"));

        // Poprzedni worker mógł wysłać wiadomość i umrzeć przed markPublished.
        // Reclaim powoduje redelivery, dlatego consumer musi deduplikować event ID.
        event.claim(Instant.parse("2030-01-01T00:02:00Z"));

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(event.getClaimedAt()).isEqualTo(Instant.parse("2030-01-01T00:02:00Z"));
    }
}
