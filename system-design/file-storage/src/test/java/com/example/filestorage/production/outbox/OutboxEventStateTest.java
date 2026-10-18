package com.example.filestorage.production.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Redelivery zwieksza licznik przy claimie, a nie podwojnie przy zapisie bledu. */
class OutboxEventStateTest {
    @Test
    void retryReturnsClaimedEventToPendingWithoutDoubleCountingAttempt() {
        OutboxEvent event = new OutboxEvent("FILE_AVAILABLE", "FILE", UUID.randomUUID(), "{}");
        event.claim(Instant.parse("2026-01-01T00:00:00Z"));

        event.retryLater("broker unavailable");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getClaimedAt()).isNull();
        assertThat(event.getLastError()).isEqualTo("broker unavailable");
    }
}
