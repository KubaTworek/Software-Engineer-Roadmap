package com.example.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.example.notification.application.NotificationService;
import com.example.notification.application.OutboxPublisher;
import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Kontrakty, których H2 ani ConcurrentHashMap nie mogą udowodnić. */
@SpringBootTest(properties = "notification.scheduling.enabled=false")
@ActiveProfiles({"infrastructure-tests", "postgres-runtime"})
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlNotificationInfrastructureIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired NotificationService notificationService;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired Ports.NotificationJobRepository jobs;
    @Autowired Ports.NotificationQueue queue;
    @Autowired Ports.CampaignRepository campaigns;
    @Autowired Ports.DigestRepository digests;

    @Test void apiAcceptanceAndOutboxRelayUseTheSamePostgresSourceOfTruth() {
        var created = notificationService.create("tenant-a", "test", "db-user-" + UUID.randomUUID(),
                NotificationType.PAYMENT_FAILED, List.of(Channel.EMAIL),
                new ContactPoint("db@example.com", null, null),
                Map.of("firstName", "DB", "invoiceId", "I-1", "amount", "10"),
                "db-idem-" + UUID.randomUUID(), null);

        outboxPublisher.publishPending();

        assertThat(jobs.findByNotificationId(created.notification().getId())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_requests WHERE notification_id=?",
                Integer.class, created.notification().getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE aggregate_id=? AND status='PUBLISHED'",
                Integer.class, created.notification().getId())).isEqualTo(1);
    }

    @Test void notificationInsertRollsBackWhenOutboxInsertFails() {
        UUID notificationId = UUID.randomUUID();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO notification_requests(notification_id,tenant_id,idempotency_key,request_fingerprint,status,created_at) VALUES(?,?,?,?,?,?)",
                    notificationId, "tenant-a", "rollback-" + UUID.randomUUID(), "fingerprint", "CREATED",
                    java.sql.Timestamp.from(Instant.now()));
            jdbc.update("INSERT INTO notification_outbox(event_id,tenant_id,aggregate_id,event_type) VALUES(?,?,?,?)",
                    UUID.randomUUID(), "tenant-a", UUID.randomUUID(), "NOTIFICATION_CREATED");
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_requests WHERE notification_id=?",
                Integer.class, notificationId)).isZero();
    }

    @Test void uniqueConstraintCoordinatesIdempotencyAcrossInstances() throws Exception {
        String key = "idem-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> insertNotification(start, key));
            var second = executor.submit(() -> insertNotification(start, key));
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("INSERTED", "DUPLICATE");
        }
    }

    @Test void skipLockedAllowsAnotherPublisherToContinueImmediately() throws Exception {
        UUID notificationId = insertNotification("outbox-" + UUID.randomUUID());
        jdbc.update("INSERT INTO notification_outbox(event_id, tenant_id, aggregate_id, event_type) VALUES (?,?,?,?)",
                UUID.randomUUID(), "tenant-a", notificationId, "NOTIFICATION_CREATED");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var owner = executor.submit(() -> transactions.execute(status -> {
                int count = jdbc.queryForList("SELECT event_id FROM notification_outbox WHERE status='PENDING' " +
                        "ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED").size();
                locked.countDown();
                await(release);
                return count;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var other = executor.submit(() -> transactions.execute(status -> jdbc.queryForList(
                    "SELECT event_id FROM notification_outbox WHERE status='PENDING' ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED").size()));
            try {
                assertThat(other.get(5, TimeUnit.SECONDS)).isZero();
            } finally {
                release.countDown();
            }
            assertThat(owner.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        }
    }

    @Test void durableQueueLeasePreventsConcurrentClaimAndAllowsCrashRecovery() {
        var created = notificationService.create("tenant-a", "test", "lease-user-" + UUID.randomUUID(),
                NotificationType.SECURITY_ALERT, List.of(Channel.EMAIL),
                new ContactPoint("lease@example.com", null, null),
                Map.of("firstName", "Lease", "eventDescription", "test"),
                "lease-idem-" + UUID.randomUUID(), null);
        outboxPublisher.publishPending();
        UUID expected = jobs.findByNotificationId(created.notification().getId()).getFirst().getId();

        assertThat(queue.pollReadyJob()).contains(expected);
        assertThat(queue.pollReadyJob()).isNotEqualTo(java.util.Optional.of(expected));
        // Symulacja śmierci procesu po zmianie stanu na PROCESSING: po lease expiry
        // kolejny worker musi odzyskać rekord, ale logika workera oznaczy go UNKNOWN.
        jdbc.update("UPDATE notification_jobs SET status='PROCESSING', lease_until=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE job_id=?", expected);
        assertThat(queue.pollReadyJob()).contains(expected);
    }

    @Test void campaignCheckpointAndDigestClaimSurviveRepositoryRoundTrip() {
        Campaign campaign = new Campaign(UUID.randomUUID(), "tenant-a", "durable-campaign",
                NotificationType.CAMPAIGN_MESSAGE, List.of("u1", "u2"), List.of(Channel.EMAIL),
                Map.of("subject", "S", "body", "B"));
        campaign.markRunning(); campaign.recipientProcessed(); campaigns.save(campaign);
        assertThat(campaigns.findById("tenant-a", campaign.getId()).orElseThrow().getNextRecipientIndex()).isEqualTo(1);

        DigestBuffer digest = new DigestBuffer(UUID.randomUUID(), "tenant-a", "user", "comments",
                Instant.now().minusSeconds(1));
        digest.addItem(Map.of("postId", "p1")); digests.save(digest);
        assertThat(digests.claimReadyToFlush(Instant.now())).extracting(DigestBuffer::getId).contains(digest.getId());
        assertThat(digests.claimReadyToFlush(Instant.now())).extracting(DigestBuffer::getId).doesNotContain(digest.getId());
    }

    @Test void campaignClaimAllowsOnlyOneConcurrentBatchOwner() throws Exception {
        Campaign campaign = new Campaign(UUID.randomUUID(), "tenant-a", "claimed-campaign",
                NotificationType.CAMPAIGN_MESSAGE, List.of("u1", "u2"), List.of(Channel.EMAIL),
                Map.of("subject", "S", "body", "B"));
        campaigns.save(campaign);
        Instant now = Instant.now();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { await(start); return campaigns.claim("tenant-a", campaign.getId(), now, now.plusSeconds(30)); });
            var second = executor.submit(() -> { await(start); return campaigns.claim("tenant-a", campaign.getId(), now, now.plusSeconds(30)); });
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .filteredOn(java.util.Optional::isPresent).hasSize(1);
        }
    }

    private String insertNotification(CountDownLatch start, String key) throws Exception {
        start.await();
        try { insertNotification(key); return "INSERTED"; }
        catch (DuplicateKeyException duplicate) { return "DUPLICATE"; }
    }

    private UUID insertNotification(String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO notification_requests(notification_id, tenant_id, idempotency_key, request_fingerprint, status, scheduled_at, created_at) VALUES (?,?,?,?,?,?,?)",
                id, "tenant-a", key, "fingerprint", "CREATED", null, Timestamp.from(Instant.now()));
        return id;
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
}
