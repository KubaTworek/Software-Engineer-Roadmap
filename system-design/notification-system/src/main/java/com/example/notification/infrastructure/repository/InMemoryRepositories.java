package com.example.notification.infrastructure.repository;

import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryRepositories {
    private InMemoryRepositories() {}

    @Repository
    @Profile("!postgres-runtime")
    public static class Notifications implements Ports.NotificationRepository {
        private final Map<UUID, Notification> storage = new ConcurrentHashMap<>();
        private final Map<String, UUID> idempotency = new ConcurrentHashMap<>();
        private final Map<String, UUID> dedupe = new ConcurrentHashMap<>();

        @Override public Notification save(Notification n) {
            storage.put(n.getId(), n);
            if (n.getIdempotencyKey() != null) idempotency.putIfAbsent(n.getTenantId() + ":" + n.getIdempotencyKey(), n.getId());
            if (n.getDeduplicationKey() != null) dedupe.put(n.getTenantId() + ":" + n.getDeduplicationKey(), n.getId());
            return n;
        }

        @Override public synchronized InsertResult insertIfAbsent(Notification candidate) {
            String idempotencyIndex = candidate.getTenantId() + ":" + candidate.getIdempotencyKey();
            if (candidate.getIdempotencyKey() != null) {
                UUID existingId = idempotency.get(idempotencyIndex);
                if (existingId != null) {
                    Notification existing = storage.get(existingId);
                    if (!Objects.equals(existing.getRequestFingerprint(), candidate.getRequestFingerprint())) {
                        throw new com.example.notification.application.Exceptions.IdempotencyConflictException(
                                "Idempotency key was already used with a different request");
                    }
                    return new InsertResult(existing, false, "IDEMPOTENCY_KEY_MATCH");
                }
            }
            save(candidate);
            return new InsertResult(candidate, true, null);
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
        @Override public int anonymizeUser(String tenantId, String userId) {
            var matches = storage.values().stream().filter(n -> n.getTenantId().equals(tenantId))
                    .filter(n -> n.getUserId().equals(userId)).toList();
            matches.forEach(Notification::anonymizePersonalData);
            return matches.size();
        }
    }

    @Repository
    @Profile("!postgres-runtime")
    public static class Jobs implements Ports.NotificationJobRepository {
        private final Map<UUID, NotificationJob> storage = new ConcurrentHashMap<>();
        private final Map<UUID, NotificationJob> dlq = new ConcurrentHashMap<>();

        @Override public NotificationJob save(NotificationJob job) { storage.put(job.getId(), job); return job; }
        @Override public synchronized NotificationJob saveIfAbsent(NotificationJob candidate) {
            return storage.values().stream()
                    .filter(existing -> existing.getNotificationId().equals(candidate.getNotificationId()))
                    .filter(existing -> existing.getChannel() == candidate.getChannel())
                    .findFirst().orElseGet(() -> save(candidate));
        }
        @Override public Optional<NotificationJob> findById(UUID id) { return Optional.ofNullable(storage.get(id)); }

        @Override public Optional<NotificationJob> findByProviderMessageId(String providerMessageId) {
            if (providerMessageId == null || providerMessageId.isBlank()) return Optional.empty();
            return storage.values().stream().filter(j -> providerMessageId.equals(j.getProviderMessageId())).findFirst();
        }

        @Override public List<NotificationJob> findByNotificationId(UUID notificationId) {
            return storage.values().stream().filter(j -> j.getNotificationId().equals(notificationId))
                    .sorted(Comparator.comparing(NotificationJob::getCreatedAt)).toList();
        }
        @Override public List<NotificationJob> findByNotificationId(String tenantId, UUID notificationId) {
            return findByNotificationId(notificationId).stream()
                    .filter(job -> job.getTenantId().equals(tenantId)).toList();
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
        @Override public int anonymizeUser(String tenantId, String userId) {
            var matches = storage.values().stream().filter(j -> j.getTenantId().equals(tenantId))
                    .filter(j -> j.getUserId().equals(userId)).toList();
            matches.forEach(NotificationJob::anonymizePersonalData);
            return matches.size();
        }
        @Override public List<NotificationJob> findReconcilableOlderThan(Instant threshold) {
            return storage.values().stream().filter(job -> job.getStatus() == NotificationStatus.UNKNOWN
                            || job.getStatus() == NotificationStatus.SENT)
                    .filter(job -> job.getUpdatedAt().isBefore(threshold)).toList();
        }
        @Override public long countDeadLetter() { return dlq.size(); }
        @Override public Optional<Instant> oldestReadyAt(Instant now) {
            return storage.values().stream().filter(job -> job.getStatus() == NotificationStatus.QUEUED)
                    .filter(job -> !job.getNextAttemptAt().isAfter(now))
                    .map(NotificationJob::getNextAttemptAt).min(Comparator.naturalOrder());
        }
    }

    @Repository
    @Profile("!postgres-runtime")
    public static class DeliveryAttempts implements Ports.DeliveryAttemptRepository {
        private final CopyOnWriteArrayList<DeliveryAttempt> storage = new CopyOnWriteArrayList<>();

        @Override public DeliveryAttempt save(DeliveryAttempt attempt) {
            storage.add(attempt);
            return attempt;
        }
        @Override public List<DeliveryAttempt> findByJobId(UUID jobId) {
            return storage.stream().filter(attempt -> attempt.jobId().equals(jobId))
                    .sorted(Comparator.comparingInt(DeliveryAttempt::number)).toList();
        }
    }

    @Repository
    @Profile("!postgres-runtime")
    public static class WebhookInbox implements Ports.WebhookInboxRepository {
        private final Map<String, WebhookInboxEvent> storage = new ConcurrentHashMap<>();
        private final Clock clock;
        public WebhookInbox(Clock clock) { this.clock = clock; }
        @Override public WebhookInboxEvent saveIfAbsent(WebhookInboxEvent event) {
            return storage.computeIfAbsent(event.getEventId(), ignored -> event);
        }
        @Override public WebhookInboxEvent save(WebhookInboxEvent event) { storage.put(event.getEventId(), event); return event; }
        @Override public List<WebhookInboxEvent> findPending() {
            Instant now = clock.instant();
            return storage.values().stream().filter(event -> !event.isProcessed() && !event.isDead())
                    .filter(event -> !event.getNextAttemptAt().isAfter(now))
                    .sorted(Comparator.comparing(WebhookInboxEvent::getReceivedAt)).toList();
        }
    }

    @Repository
    @Profile("!postgres-runtime")
    public static class Suppressions implements Ports.SuppressionRepository {
        private final Map<String, SuppressionEntry> storage = new ConcurrentHashMap<>();
        @Override public SuppressionEntry save(SuppressionEntry entry) {
            storage.put(key(entry.tenantId(), entry.channel(), entry.contactHash()), entry); return entry;
        }
        @Override public boolean exists(String tenantId, Channel channel, String contactHash) {
            return storage.containsKey(key(tenantId, channel, contactHash));
        }
        @Override public void delete(String tenantId, Channel channel, String contactHash) {
            storage.remove(key(tenantId, channel, contactHash));
        }
        @Override public void deleteForTenant(String tenantId) {
            storage.entrySet().removeIf(entry -> entry.getValue().tenantId().equals(tenantId));
        }
        private String key(String tenant, Channel channel, String hash) { return tenant + ":" + channel + ":" + hash; }
    }

    @Repository
    @Profile("!postgres-runtime")
    public static class Outbox implements Ports.OutboxRepository {
        private final Map<UUID, OutboxEvent> storage = new ConcurrentHashMap<>();
        private final Clock clock;
        public Outbox(Clock clock) { this.clock = clock; }

        @Override public OutboxEvent save(OutboxEvent e) { storage.put(e.getId(), e); return e; }

        @Override public List<OutboxEvent> findPending(int limit) {
            return storage.values().stream().filter(e -> e.getStatus() == OutboxStatus.PENDING)
                    .filter(e -> !e.getNextAttemptAt().isAfter(clock.instant()))
                    .sorted(Comparator.comparing(OutboxEvent::getCreatedAt)).limit(limit).toList();
        }

        @Override public List<OutboxEvent> findAll(String tenantId) {
            return storage.values().stream().filter(e -> e.getTenantId().equals(tenantId))
                    .sorted(Comparator.comparing(OutboxEvent::getCreatedAt).reversed()).toList();
        }
        @Override public long countPending() {
            return storage.values().stream().filter(e -> e.getStatus() == OutboxStatus.PENDING).count();
        }
        @Override public Optional<Instant> oldestPendingAt() {
            return storage.values().stream().filter(e -> e.getStatus() == OutboxStatus.PENDING)
                    .map(OutboxEvent::getCreatedAt).min(Comparator.naturalOrder());
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
        @Override public void anonymizeUser(String tenantId, String userId) {
            events.replaceAll(event -> event.tenantId().equals(tenantId) && event.actor().equals(userId)
                    ? new AuditEvent(event.id(), event.tenantId(), "erased-user", event.action(), event.resourceId(),
                    Map.of("redacted", true), event.createdAt()) : event);
        }
    }

    @Repository
    @Profile("!postgres-runtime")
    public static class Campaigns implements Ports.CampaignRepository {
        private final Map<UUID, Campaign> storage = new ConcurrentHashMap<>();
        private final Map<UUID, ClaimState> claims = new ConcurrentHashMap<>();
        private record ClaimState(UUID token, Instant until) {}

        @Override public Campaign save(Campaign c) { storage.put(c.getId(), c); return c; }
        @Override public Optional<Campaign> findById(String tenantId, UUID id) { return Optional.ofNullable(storage.get(id)).filter(c -> c.getTenantId().equals(tenantId)); }
        @Override public List<Campaign> findAll(String tenantId) {
            return storage.values().stream().filter(c -> c.getTenantId().equals(tenantId)).sorted(Comparator.comparing(Campaign::getCreatedAt).reversed()).toList();
        }
        @Override public synchronized Optional<Ports.CampaignRepository.Claim> claim(
                String tenantId, UUID id, Instant now, Instant leaseUntil) {
            Campaign campaign = findById(tenantId, id).orElse(null);
            if (campaign == null || !campaign.hasMoreRecipients()) return Optional.empty();
            ClaimState current = claims.get(id);
            if (current != null && current.until().isAfter(now)) return Optional.empty();
            UUID token = UUID.randomUUID();
            claims.put(id, new ClaimState(token, leaseUntil));
            return Optional.of(new Ports.CampaignRepository.Claim(campaign, token));
        }
        @Override public synchronized boolean checkpoint(Ports.CampaignRepository.Claim claim) {
            ClaimState owner = claims.get(claim.campaign().getId());
            if (owner == null || !owner.token().equals(claim.token())) return false;
            storage.put(claim.campaign().getId(), claim.campaign());
            return true;
        }
        @Override public synchronized void release(Ports.CampaignRepository.Claim claim) {
            claims.computeIfPresent(claim.campaign().getId(), (id, owner) ->
                    owner.token().equals(claim.token()) ? null : owner);
        }
    }

    @Repository
    @Profile("!postgres-runtime")
    public static class Digests implements Ports.DigestRepository {
        private final Map<UUID, DigestBuffer> storage = new ConcurrentHashMap<>();

        @Override public DigestBuffer save(DigestBuffer buffer) { storage.put(buffer.getId(), buffer); return buffer; }

        @Override public Optional<DigestBuffer> findOpen(String tenantId, String userId, String digestKey) {
            return storage.values().stream().filter(b -> b.getTenantId().equals(tenantId))
                    .filter(b -> b.getUserId().equals(userId)).filter(b -> b.getDigestKey().equals(digestKey))
                    .filter(b -> !b.isFlushed()).findFirst();
        }

        @Override public List<DigestBuffer> findReadyToFlush(Instant now) {
            return storage.values().stream().filter(b -> !b.isFlushed() && !b.isClaimed()).filter(b -> !b.getFlushAt().isAfter(now)).toList();
        }

        @Override public synchronized List<DigestBuffer> claimReadyToFlush(Instant now) {
            return findReadyToFlush(now).stream().filter(DigestBuffer::tryClaim).toList();
        }
    }
}
