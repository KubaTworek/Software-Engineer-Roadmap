package com.example.notification;

import com.example.notification.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Model-based smoke test: losowy wybór terminalnej obserwacji nie może dopuścić cofnięcia stanu. */
class DeliveryHistoryModelTest {
    @Test void terminalStateRemainsTerminalAcrossGeneratedFollowUpOperations() {
        Random random = new Random(20260925L);
        for (int example = 0; example < 250; example++) {
            NotificationJob job = job();
            job.markProcessing();
            job.markSent(new ProviderSendResult("provider", "message-" + example), false);
            if (random.nextBoolean()) job.markDelivered(); else job.markBounced("terminal");
            NotificationStatus terminal = job.getStatus();

            for (int operation = 0; operation < 20; operation++) {
                if (terminal == NotificationStatus.DELIVERED) job.markDelivered();
                else job.markBounced("duplicate");
                assertThat(job.getStatus()).isEqualTo(terminal);
                assertThat(job.isTerminal()).isTrue();
            }
        }
    }

    @Test void outboxUsesBackoffAndEventuallyBecomesDead() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "tenant", UUID.randomUUID(), "CREATED");
        event.markFailed("one", now, 3);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getNextAttemptAt()).isAfter(now);
        event.markFailed("two", now.plusSeconds(10), 3);
        event.markFailed("three", now.plusSeconds(20), 3);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(event.getAttempts()).isEqualTo(3);
    }

    @Test void poisonWebhookStopsConsumingRetryCapacity() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        WebhookInboxEvent event = new WebhookInboxEvent("event", "unknown", ProviderWebhookStatus.DELIVERED, null, now);
        event.markFailed("missing operation", now, 2);
        assertThat(event.isDead()).isFalse();
        event.markFailed("still missing", now.plusSeconds(10), 2);
        assertThat(event.isDead()).isTrue();
    }

    private NotificationJob job() {
        return new NotificationJob(UUID.randomUUID(), UUID.randomUUID(), "tenant", "user",
                NotificationType.PAYMENT_FAILED, Channel.EMAIL, new ContactPoint("a@b.pl", null, null),
                Map.of(), 3, null, null, UUID.randomUUID(), 1);
    }
}
