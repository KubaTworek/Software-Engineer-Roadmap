package com.example.notification.infrastructure.repository;

import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryRepositories {
    private InMemoryRepositories() {}

    @Repository
    public static class Notifications implements Ports.NotificationRepository {
        private final Map<UUID, Notification> storage = new ConcurrentHashMap<>();
        private final Map<String, UUID> idempotency = new ConcurrentHashMap<>();
        private final Map<String, UUID> dedupe = new ConcurrentHashMap<>();

        @Override public Notification save(Notification n) {
            storage.put(n.getId(), n);
            if (n.getIdempotencyKey() != null) idempotency.putIfAbsent(n.getTenantId() + ":" + n.getIdempotencyKey(), n.getId());
            if (n.getDeduplicationKey() != null) dedupe.putIfAbsent(n.getTenantId() + ":" + n.getDeduplicationKey(), n.getId());
            return n;
        }

        @Override public Optional<Notification> findById(String tenantId, UUID id) {
            return Optional.ofNullable(storage.get(id)).filter(n -> n.getTenantId().equals(tenantId));
        }

        @Override public List<Notification> findAll(String tenantId) {
            return storage.values().stream().filter(n -> n.getTenantId().equals(tenantId))
                    .sorted(Comparator.comparing(Notification::getCreatedAt).reversed()).toList();
        }

        @Override public List<Notification> findTerminalOlderThan(Instant threshold) {
            return storage.values().stream().filter(Notification::isTerminal)
                    .filter(n -> n.getStatus() != NotificationStatus.ARCHIVED)
                    .filter(n -> n.getUpdatedAt().isBefore(threshold)).toList();
        }

        @Override public Optional<Notification> findByIdempotencyKey(String tenantId, String key) {
            if (key == null || key.isBlank()) return Optional.empty();
            return Optional.ofNullable(idempotency.get(tenantId + ":" + key)).flatMap(id -> findById(tenantId, id));
        }

        @Override public Optional<Notification> findByDeduplicationKey(String tenantId, String key) {
            if (key == null || key.isBlank()) return Optional.empty();
            return Optional.ofNullable(dedupe.get(tenantId + ":" + key)).flatMap(id -> findById(tenantId, id));
        }
    }

    @Repository
    public static class Jobs implements Ports.NotificationJobRepository {
        private final Map<UUID, NotificationJob> storage = new ConcurrentHashMap<>();
        private final Map<UUID, NotificationJob> dlq = new ConcurrentHashMap<>();

        @Override public NotificationJob save(NotificationJob job) { storage.put(job.getId(), job); return job; }
        @Override public Optional<NotificationJob> findById(UUID id) { return Optional.ofNullable(storage.get(id)); }

        @Override public Optional<NotificationJob> findByProviderMessageId(String providerMessageId) {
            if (providerMessageId == null || providerMessageId.isBlank()) return Optional.empty();
            return storage.values().stream().filter(j -> providerMessageId.equals(j.getProviderMessageId())).findFirst();
        }

        @Override public List<NotificationJob> findByNotificationId(UUID notificationId) {
            return storage.values().stream().filter(j -> j.getNotificationId().equals(notificationId))
                    .sorted(Comparator.comparing(NotificationJob::getCreatedAt)).toList();
        }

        @Override public List<NotificationJob> findAll(String tenantId) {
            return storage.values().stream().filter(j -> j.getTenantId().equals(tenantId))
                    .sorted(Comparator.comparing(NotificationJob::getCreatedAt).reversed()).toList();
        }

        @Override public List<NotificationJob> findDeadLetterJobs(String tenantId) {
            return dlq.values().stream().filter(j -> j.getTenantId().equals(tenantId))
                    .sorted(Comparator.comparing(NotificationJob::getUpdatedAt).reversed()).toList();
        }

        @Override public void moveToDeadLetter(NotificationJob job) { dlq.put(job.getId(), job); }
    }

    @Repository
    public static class Outbox implements Ports.OutboxRepository {
        private final Map<UUID, OutboxEvent> storage = new ConcurrentHashMap<>();

        @Override public OutboxEvent save(OutboxEvent e) { storage.put(e.getId(), e); return e; }

        @Override public List<OutboxEvent> findPending(int limit) {
            return storage.values().stream().filter(e -> e.getStatus() == OutboxStatus.PENDING)
                    .sorted(Comparator.comparing(OutboxEvent::getCreatedAt)).limit(limit).toList();
        }

        @Override public List<OutboxEvent> findAll(String tenantId) {
            return storage.values().stream().filter(e -> e.getTenantId().equals(tenantId))
                    .sorted(Comparator.comparing(OutboxEvent::getCreatedAt).reversed()).toList();
        }
    }

    @Repository
    public static class Templates implements Ports.TemplateRepository {
        private final Map<UUID, NotificationTemplate> storage = new ConcurrentHashMap<>();

        @PostConstruct
        void seed() {
            seedTenant("default");
            seedTenant("tenant-a");
        }

        private void seedTenant(String tenantId) {
            add(tenantId, NotificationType.PASSWORD_RESET, Channel.EMAIL, "password-reset-email", "Reset your password", "Hi {{firstName}}, reset: {{resetLink}}", Set.of("firstName", "resetLink"));
            add(tenantId, NotificationType.PASSWORD_RESET, Channel.SMS, "password-reset-sms", "Password reset", "Reset: {{resetLink}}", Set.of("resetLink"));
            add(tenantId, NotificationType.PAYMENT_FAILED, Channel.EMAIL, "payment-failed-email", "Payment failed", "Hi {{firstName}}, invoice {{invoiceId}} failed. Amount: {{amount}}.", Set.of("firstName", "invoiceId", "amount"));
            add(tenantId, NotificationType.PAYMENT_FAILED, Channel.PUSH, "payment-failed-push", "Payment failed", "Your payment for {{amount}} failed.", Set.of("amount"));
            add(tenantId, NotificationType.PAYMENT_FAILED, Channel.IN_APP, "payment-failed-in-app", "Payment failed", "Invoice {{invoiceId}} failed. Amount: {{amount}}.", Set.of("invoiceId", "amount"));
            add(tenantId, NotificationType.SECURITY_ALERT, Channel.EMAIL, "security-alert-email", "Security alert", "Hi {{firstName}}, event: {{eventDescription}}.", Set.of("firstName", "eventDescription"));
            add(tenantId, NotificationType.SECURITY_ALERT, Channel.SMS, "security-alert-sms", "Security alert", "{{eventDescription}}", Set.of("eventDescription"));
            add(tenantId, NotificationType.SECURITY_ALERT, Channel.PUSH, "security-alert-push", "Security alert", "{{eventDescription}}", Set.of("eventDescription"));
            add(tenantId, NotificationType.SECURITY_ALERT, Channel.IN_APP, "security-alert-in-app", "Security alert", "{{eventDescription}}", Set.of("eventDescription"));
            add(tenantId, NotificationType.MARKETING_PROMOTION, Channel.EMAIL, "marketing-email", "Special offer", "Hi {{firstName}}, offer: {{offerName}}", Set.of("firstName", "offerName"));
            add(tenantId, NotificationType.MARKETING_PROMOTION, Channel.PUSH, "marketing-push", "Special offer", "{{offerName}}", Set.of("offerName"));
            add(tenantId, NotificationType.MARKETING_PROMOTION, Channel.IN_APP, "marketing-inapp", "Special offer", "{{offerName}}", Set.of("offerName"));
            add(tenantId, NotificationType.WEEKLY_DIGEST, Channel.EMAIL, "digest-email", "Your digest", "{{itemCount}} new items in {{digestKey}}: {{items}}", Set.of("itemCount", "digestKey", "items"));
            add(tenantId, NotificationType.WEEKLY_DIGEST, Channel.IN_APP, "digest-inapp", "Your digest", "{{itemCount}} new items in {{digestKey}}", Set.of("itemCount", "digestKey"));
            add(tenantId, NotificationType.CAMPAIGN_MESSAGE, Channel.EMAIL, "campaign-email", "{{subject}}", "{{body}}", Set.of("subject", "body"));
            add(tenantId, NotificationType.CAMPAIGN_MESSAGE, Channel.PUSH, "campaign-push", "{{subject}}", "{{body}}", Set.of("subject", "body"));
        }

        private void add(String tenantId, NotificationType type, Channel channel, String key, String subject, String body, Set<String> vars) {
            save(new NotificationTemplate(UUID.randomUUID(), tenantId, key, type, channel, 1, subject, body, vars, true));
        }

        @Override public NotificationTemplate save(NotificationTemplate t) { storage.put(t.getId(), t); return t; }
        @Override public Optional<NotificationTemplate> findById(String tenantId, UUID id) { return Optional.ofNullable(storage.get(id)).filter(t -> t.getTenantId().equals(tenantId)); }

        @Override public Optional<NotificationTemplate> findActiveByTypeAndChannel(String tenantId, NotificationType type, Channel channel) {
            return storage.values().stream().filter(t -> t.getTenantId().equals(tenantId)).filter(NotificationTemplate::isActive)
                    .filter(t -> t.getNotificationType() == type && t.getChannel() == channel)
                    .max(Comparator.comparing(NotificationTemplate::getVersion));
        }

        @Override public List<NotificationTemplate> findAll(String tenantId) {
            return storage.values().stream().filter(t -> t.getTenantId().equals(tenantId))
                    .sorted(Comparator.comparing(NotificationTemplate::getUpdatedAt).reversed()).toList();
        }

        @Override public void delete(String tenantId, UUID id) { findById(tenantId, id).ifPresent(NotificationTemplate::deactivate); }
    }

    @Repository
    public static class Audit implements Ports.AuditRepository {
        private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

        @Override public AuditEvent save(AuditEvent event) { events.add(event); return event; }

        @Override public List<AuditEvent> findAll(String tenantId) {
            return events.stream().filter(e -> e.tenantId().equals(tenantId) || "system".equals(tenantId))
                    .sorted(Comparator.comparing(AuditEvent::createdAt).reversed()).toList();
        }

        @Override public List<AuditEvent> findOlderThan(Instant threshold) {
            return events.stream().filter(e -> e.createdAt().isBefore(threshold)).toList();
        }

        @Override public void delete(AuditEvent event) { events.remove(event); }
    }

    @Repository
    public static class Campaigns implements Ports.CampaignRepository {
        private final Map<UUID, Campaign> storage = new ConcurrentHashMap<>();

        @Override public Campaign save(Campaign c) { storage.put(c.getId(), c); return c; }
        @Override public Optional<Campaign> findById(String tenantId, UUID id) { return Optional.ofNullable(storage.get(id)).filter(c -> c.getTenantId().equals(tenantId)); }
        @Override public List<Campaign> findAll(String tenantId) {
            return storage.values().stream().filter(c -> c.getTenantId().equals(tenantId)).sorted(Comparator.comparing(Campaign::getCreatedAt).reversed()).toList();
        }
    }

    @Repository
    public static class Digests implements Ports.DigestRepository {
        private final Map<UUID, DigestBuffer> storage = new ConcurrentHashMap<>();

        @Override public DigestBuffer save(DigestBuffer buffer) { storage.put(buffer.getId(), buffer); return buffer; }

        @Override public Optional<DigestBuffer> findOpen(String tenantId, String userId, String digestKey) {
            return storage.values().stream().filter(b -> b.getTenantId().equals(tenantId))
                    .filter(b -> b.getUserId().equals(userId)).filter(b -> b.getDigestKey().equals(digestKey))
                    .filter(b -> !b.isFlushed()).findFirst();
        }

        @Override public List<DigestBuffer> findReadyToFlush(Instant now) {
            return storage.values().stream().filter(b -> !b.isFlushed()).filter(b -> !b.getFlushAt().isAfter(now)).toList();
        }
    }
}
