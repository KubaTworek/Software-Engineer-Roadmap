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
        Notification save(Notification notification);
        Optional<Notification> findById(String tenantId, UUID id);
        List<Notification> findAll(String tenantId);
        List<Notification> findTerminalOlderThan(Instant threshold);
        Optional<Notification> findByIdempotencyKey(String tenantId, String idempotencyKey);
        Optional<Notification> findByDeduplicationKey(String tenantId, String deduplicationKey);
    }

    public interface NotificationJobRepository {
        NotificationJob save(NotificationJob job);
        Optional<NotificationJob> findById(UUID id);
        Optional<NotificationJob> findByProviderMessageId(String providerMessageId);
        List<NotificationJob> findByNotificationId(UUID notificationId);
        List<NotificationJob> findAll(String tenantId);
        List<NotificationJob> findDeadLetterJobs(String tenantId);
        void moveToDeadLetter(NotificationJob job);
    }

    public interface OutboxRepository {
        OutboxEvent save(OutboxEvent event);
        List<OutboxEvent> findPending(int limit);
        List<OutboxEvent> findAll(String tenantId);
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
    }

    public interface NotificationProvider {
        Channel channel();
        String providerName();
        ProviderSendResult send(String tenantId, String recipient, RenderedNotification notification);
    }

    public interface AuditRepository {
        AuditEvent save(AuditEvent event);
        List<AuditEvent> findAll(String tenantId);
        List<AuditEvent> findOlderThan(Instant threshold);
        void delete(AuditEvent event);
    }

    public interface CampaignRepository {
        Campaign save(Campaign campaign);
        Optional<Campaign> findById(String tenantId, UUID id);
        List<Campaign> findAll(String tenantId);
    }

    public interface DigestRepository {
        DigestBuffer save(DigestBuffer buffer);
        Optional<DigestBuffer> findOpen(String tenantId, String userId, String digestKey);
        List<DigestBuffer> findReadyToFlush(Instant now);
    }
}
