package com.example.notification;

import com.example.notification.application.*;
import com.example.notification.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "notification.worker.fixed-delay-ms=3600000",
        "notification.outbox.fixed-delay-ms=3600000",
        "notification.digest.fixed-delay-ms=3600000",
        "notification.archive.fixed-delay-ms=3600000",
        "notification.reconciliation.fixed-delay-ms=3600000",
        "notification.webhook.inbox-retry-ms=3600000",
        "notification.rate-limit.tenant-per-minute=10000",
        "notification.rate-limit.user-per-minute=10000"
})
class NotificationGuaranteesTest {
    @Autowired NotificationService notifications;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired NotificationWorker worker;
    @Autowired Ports.NotificationRepository notificationRepository;
    @Autowired Ports.NotificationJobRepository jobRepository;
    @Autowired Ports.PreferenceService preferences;
    @Autowired Ports.TemplateRepository templates;
    @Autowired Ports.DeliveryAttemptRepository attempts;
    @Autowired ProviderWebhookService webhooks;
    @Autowired DeliveryReconciliationService reconciliation;
    @Autowired PersonalDataLifecycleService lifecycle;
    @Autowired PostProviderEffectProbe effectProbe;

    @Test
    void sameIdempotencyKeyWithDifferentRequestIsAConflict() {
        String key = "idem-" + UUID.randomUUID();
        create("user-a", "user@example.com", key, Map.of("firstName", "A", "invoiceId", "1", "amount", "10"));

        assertThatThrownBy(() -> create("user-a", "user@example.com", key,
                Map.of("firstName", "A", "invoiceId", "1", "amount", "20")))
                .isInstanceOf(Exceptions.IdempotencyConflictException.class);
    }

    @Test
    void replayingOutboxDoesNotCreateSecondJobForTheSameChannel() {
        var result = create("user-" + UUID.randomUUID(), "user@example.com", "outbox-" + UUID.randomUUID(),
                Map.of("firstName", "A", "invoiceId", "1", "amount", "10"));
        outboxPublisher.publishPending();
        outboxPublisher.publishPending();

        assertThat(jobRepository.findByNotificationId(result.notification().getId())).hasSize(1);
    }

    @Test
    void marketingOptOutAfterQueueingStillStopsTheExternalEffect() {
        String user = "optout-" + UUID.randomUUID();
        var created = notifications.create("tenant-a", "test", user, NotificationType.MARKETING_PROMOTION,
                List.of(Channel.EMAIL), new ContactPoint("user@example.com", null, null),
                Map.of("firstName", "A", "offerName", "Sale"), "optout-" + UUID.randomUUID(), null);
        outboxPublisher.publishPending();
        preferences.updatePreferences("tenant-a", user,
                Map.of(NotificationType.MARKETING_PROMOTION, Map.of(Channel.EMAIL, false)));
        NotificationJob job = jobRepository.findByNotificationId(created.notification().getId()).getFirst();

        worker.processJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
    }

    @Test
    void ambiguousProviderTimeoutDoesNotFallBackOrRetry() {
        var created = create("unknown-" + UUID.randomUUID(), "unknown-outcome@example.com",
                "unknown-" + UUID.randomUUID(), Map.of("firstName", "A", "invoiceId", "1", "amount", "10"));
        outboxPublisher.publishPending();
        NotificationJob job = jobRepository.findByNotificationId(created.notification().getId()).getFirst();

        worker.processJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(NotificationStatus.UNKNOWN);
        assertThat(job.isFallbackUsed()).isFalse();
        assertThat(job.getAttemptCount()).isZero();
        assertThat(attempts.findByJobId(job.getId()))
                .singleElement()
                .extracting(DeliveryAttempt::outcome)
                .isEqualTo(DeliveryAttempt.Outcome.UNKNOWN);
    }

    @Test
    void crashAfterProviderAcceptanceRecoversAsUnknownWithoutSecondExternalCall() {
        var created = create("crash-" + UUID.randomUUID(), "user@example.com", "crash-" + UUID.randomUUID(),
                Map.of("firstName", "A", "invoiceId", "1", "amount", "10"));
        outboxPublisher.publishPending();
        NotificationJob job = jobRepository.findByNotificationId(created.notification().getId()).getFirst();

        effectProbe.simulateCrashOnNextAcceptance();
        assertThatThrownBy(() -> worker.processJob(job.getId()))
                .isInstanceOf(PostProviderEffectProbe.SimulatedProcessCrash.class);
        assertThat(job.getStatus()).isEqualTo(NotificationStatus.PROCESSING);

        worker.processJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(NotificationStatus.UNKNOWN);
        assertThat(attempts.findByJobId(job.getId())).hasSize(1);
    }

    @Test
    void jobPinsTemplateVersionAndScheduledJobIsNotReadyEarly() {
        Instant future = Instant.now().plusSeconds(3600);
        var created = notifications.create("tenant-a", "test", "scheduled-" + UUID.randomUUID(),
                NotificationType.PAYMENT_FAILED, List.of(Channel.EMAIL),
                new ContactPoint("user@example.com", null, null),
                Map.of("firstName", "A", "invoiceId", "1", "amount", "10"),
                "scheduled-" + UUID.randomUUID(), null, future);
        outboxPublisher.publishPending();
        NotificationJob job = jobRepository.findByNotificationId(created.notification().getId()).getFirst();
        NotificationTemplate pinned = templates.findById("tenant-a", job.getTemplateId()).orElseThrow();

        assertThat(job.getTemplateVersion()).isEqualTo(pinned.getVersion());
        assertThat(job.getNextAttemptAt()).isEqualTo(future);
    }

    @Test
    void stateMachineRejectsDeliveryBeforeProviderAcceptedTheJob() {
        NotificationJob job = new NotificationJob(UUID.randomUUID(), UUID.randomUUID(), "tenant-a", "user",
                NotificationType.PAYMENT_FAILED, Channel.EMAIL, new ContactPoint("a@b.pl", null, null), Map.of(),
                3, null, null, UUID.randomUUID(), 1);
        assertThatThrownBy(job::markDelivered).isInstanceOf(IllegalStateException.class);
    }

    @Test void webhookInboxMakesProviderRedeliveryIdempotent() {
        var created = create("callback-" + UUID.randomUUID(), "callback@example.com", "callback-" + UUID.randomUUID(),
                Map.of("firstName", "A", "invoiceId", "1", "amount", "10"));
        outboxPublisher.publishPending();
        NotificationJob job = jobRepository.findByNotificationId(created.notification().getId()).getFirst();
        worker.processJob(job.getId());
        assertThat(webhooks.accept("event-1-" + UUID.randomUUID(), job.getProviderMessageId(), ProviderWebhookStatus.DELIVERED, null)).isTrue();
        String duplicateId = "duplicate-" + UUID.randomUUID();
        assertThat(webhooks.accept(duplicateId, job.getProviderMessageId(), ProviderWebhookStatus.DELIVERED, null)).isTrue();
        assertThat(webhooks.accept(duplicateId, job.getProviderMessageId(), ProviderWebhookStatus.DELIVERED, null)).isFalse();
    }

    @Test void reconciliationTurnsAmbiguousAttemptIntoProviderAcceptedState() {
        var created = create("reconcile-" + UUID.randomUUID(), "unknown-outcome-reconcile-accepted@example.com",
                "reconcile-" + UUID.randomUUID(), Map.of("firstName", "A", "invoiceId", "1", "amount", "10"));
        outboxPublisher.publishPending();
        NotificationJob job = jobRepository.findByNotificationId(created.notification().getId()).getFirst();
        worker.processJob(job.getId());
        assertThat(reconciliation.reconcile(job)).isEqualTo(Ports.ProviderLookupResult.ACCEPTED);
        assertThat(job.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test void reconciliationClosesSentJobWhenProviderCallbackWasLost() {
        var created = create("delivered-" + UUID.randomUUID(), "reconcile-delivered@example.com",
                "delivered-" + UUID.randomUUID(), Map.of("firstName", "A", "invoiceId", "1", "amount", "10"));
        outboxPublisher.publishPending();
        NotificationJob job = jobRepository.findByNotificationId(created.notification().getId()).getFirst();
        worker.processJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(reconciliation.reconcile(job)).isEqualTo(Ports.ProviderLookupResult.DELIVERED);
        assertThat(job.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(notificationRepository.findById("tenant-a", created.notification().getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test void erasureRemovesPayloadAndContactButPreservesTechnicalHistory() {
        String user = "erase-" + UUID.randomUUID();
        var created = create(user, "personal@example.com", "erase-" + UUID.randomUUID(),
                Map.of("firstName", "Personal", "invoiceId", "1", "amount", "10"));
        outboxPublisher.publishPending();
        assertThat(lifecycle.erase("tenant-a", user)).extracting(PersonalDataLifecycleService.ErasureResult::notifications,
                PersonalDataLifecycleService.ErasureResult::jobs).containsExactly(1, 1);
        Notification erased = notificationRepository.findById("tenant-a", created.notification().getId()).orElseThrow();
        assertThat(erased.getUserId()).startsWith("erased:");
        assertThat(erased.getPayload()).isEmpty();
        assertThat(erased.getContactPoint().email()).isNull();
    }

    @Test void hardBounceSuppressesTheContactPointForFutureJobs() {
        String email = "bounced-" + UUID.randomUUID() + "@example.com";
        var first = create("bounce-source-" + UUID.randomUUID(), email, "bounce-" + UUID.randomUUID(),
                Map.of("firstName", "A", "invoiceId", "1", "amount", "10"));
        outboxPublisher.publishPending();
        NotificationJob bounced = jobRepository.findByNotificationId(first.notification().getId()).getFirst();
        worker.processJob(bounced.getId());
        webhooks.accept("bounce-event-" + UUID.randomUUID(), bounced.getProviderMessageId(),
                ProviderWebhookStatus.BOUNCED, "hard bounce");

        var second = create("bounce-target-" + UUID.randomUUID(), email, "after-bounce-" + UUID.randomUUID(),
                Map.of("firstName", "B", "invoiceId", "2", "amount", "20"));
        outboxPublisher.publishPending();
        NotificationJob suppressed = jobRepository.findByNotificationId(second.notification().getId()).getFirst();
        worker.processJob(suppressed.getId());
        assertThat(suppressed.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
    }

    @Test void laterConflictingWebhookCannotDowngradeDeliveredState() {
        var created = create("ordered-" + UUID.randomUUID(), "ordered@example.com", "ordered-" + UUID.randomUUID(),
                Map.of("firstName", "A", "invoiceId", "1", "amount", "10"));
        outboxPublisher.publishPending();
        NotificationJob job = jobRepository.findByNotificationId(created.notification().getId()).getFirst();
        worker.processJob(job.getId());
        webhooks.accept("delivered-" + UUID.randomUUID(), job.getProviderMessageId(),
                ProviderWebhookStatus.DELIVERED, null);
        webhooks.accept("late-bounce-" + UUID.randomUUID(), job.getProviderMessageId(),
                ProviderWebhookStatus.BOUNCED, "late conflicting observation");
        assertThat(job.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    private CreateNotificationResult create(String user, String email, String key, Map<String, Object> payload) {
        return notifications.create("tenant-a", "test", user, NotificationType.PAYMENT_FAILED,
                List.of(Channel.EMAIL), new ContactPoint(email, null, null), payload, key, null);
    }
}
