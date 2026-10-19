package com.example.notification.application;

import com.example.notification.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Ports {
    private Ports() {}

    public interface NotificationRepository {
        record InsertResult(Notification notification, boolean inserted, String duplicateReason) {}
        Notification save(Notification notification);
        InsertResult insertIfAbsent(Notification notification);
        Optional<Notification> findById(String tenantId, UUID id);
        List<Notification> findAll(String tenantId);
        List<Notification> findTerminalOlderThan(Instant threshold);
        Optional<Notification> findByIdempotencyKey(String tenantId, String idempotencyKey);
        Optional<Notification> findByDeduplicationKey(String tenantId, String deduplicationKey);
        int anonymizeUser(String tenantId, String userId);
    }

    public interface NotificationJobRepository {
        NotificationJob save(NotificationJob job);
        NotificationJob saveIfAbsent(NotificationJob job);
        Optional<NotificationJob> findById(UUID id);
        Optional<NotificationJob> findByProviderMessageId(String providerMessageId);
        List<NotificationJob> findByNotificationId(UUID notificationId);
        List<NotificationJob> findByNotificationId(String tenantId, UUID notificationId);
        List<NotificationJob> findAll(String tenantId);
        List<NotificationJob> findDeadLetterJobs(String tenantId);
        List<NotificationJob> findReconcilableOlderThan(Instant threshold);
        void moveToDeadLetter(NotificationJob job);
        int anonymizeUser(String tenantId, String userId);
        long countDeadLetter();
        Optional<Instant> oldestReadyAt(Instant now);
    }

    public enum ProviderLookupResult { ACCEPTED, DELIVERED, NOT_SENT, STILL_UNKNOWN }
    public interface ProviderStatusGateway {
        ProviderLookupResult lookup(NotificationJob job, DeliveryAttempt attempt);
    }

    public interface DeliveryAttemptRepository {
        DeliveryAttempt save(DeliveryAttempt attempt);
        List<DeliveryAttempt> findByJobId(UUID jobId);
    }

    public interface WebhookInboxRepository {
        WebhookInboxEvent saveIfAbsent(WebhookInboxEvent event);
        WebhookInboxEvent save(WebhookInboxEvent event);
        List<WebhookInboxEvent> findPending();
    }

    public interface SuppressionRepository {
        SuppressionEntry save(SuppressionEntry entry);
        boolean exists(String tenantId, Channel channel, String contactHash);
        void delete(String tenantId, Channel channel, String contactHash);
        void deleteForTenant(String tenantId);
    }

    public interface OutboxRepository {
        OutboxEvent save(OutboxEvent event);
        List<OutboxEvent> findPending(int limit);
        List<OutboxEvent> findAll(String tenantId);
        long countPending();
        Optional<Instant> oldestPendingAt();
    }

    public interface NotificationQueue {
        void enqueue(UUID jobId);
        Optional<UUID> pollReadyJob();
        int size();
    }

    public interface TemplateRepository {
        NotificationTemplate save(NotificationTemplate template);
        Optional<NotificationTemplate> findById(String tenantId, UUID id);
        Optional<NotificationTemplate> findActiveByTypeAndChannel(String tenantId, NotificationType type, Channel channel);
        List<NotificationTemplate> findAll(String tenantId);
        void delete(String tenantId, UUID id);
    }

    public interface TemplateRenderer {
        RenderedNotification render(NotificationTemplate template, Map<String, Object> payload);
    }

    public interface PreferenceService {
        List<Channel> resolveChannels(String tenantId, String userId, NotificationType type, List<Channel> requestedChannels);
        Map<NotificationType, Map<Channel, Boolean>> getPreferences(String tenantId, String userId);
        Map<NotificationType, Map<Channel, Boolean>> updatePreferences(String tenantId, String userId, Map<NotificationType, Map<Channel, Boolean>> preferences);
        boolean isAllowedNow(String tenantId, String userId, NotificationType type, Channel channel);
        void deleteUser(String tenantId, String userId);
    }

    public interface NotificationProvider {
        Channel channel();
        String providerName();
        ProviderSendResult send(String tenantId, String recipient, RenderedNotification notification, String idempotencyKey);
    }

    public interface AuditRepository {
        AuditEvent save(AuditEvent event);
        List<AuditEvent> findAll(String tenantId);
        List<AuditEvent> findOlderThan(Instant threshold);
        void delete(AuditEvent event);
        void anonymizeUser(String tenantId, String userId);
    }

    public interface CampaignRepository {
        record Claim(Campaign campaign, UUID token) {}
        Campaign save(Campaign campaign);
        Optional<Campaign> findById(String tenantId, UUID id);
        List<Campaign> findAll(String tenantId);
        Optional<Claim> claim(String tenantId, UUID id, Instant now, Instant leaseUntil);
        boolean checkpoint(Claim claim);
        void release(Claim claim);
    }

    /** Fault-injection seam located exactly after the external effect and before local acknowledgement. */
    public interface PostProviderEffectHook {
        void afterAccepted(NotificationJob job, ProviderSendResult result);
    }

    public interface DigestRepository {
        DigestBuffer save(DigestBuffer buffer);
        Optional<DigestBuffer> findOpen(String tenantId, String userId, String digestKey);
        List<DigestBuffer> findReadyToFlush(Instant now);
        List<DigestBuffer> claimReadyToFlush(Instant now);
    }
}
