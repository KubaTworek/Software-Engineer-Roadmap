package com.example.filestorage.infrastructure;

import com.example.filestorage.auth.AppUser;
import com.example.filestorage.auth.UserRepository;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.file.FileStatus;
import com.example.filestorage.production.outbox.OutboxEventRepository;
import com.example.filestorage.production.outbox.IdempotentFileEventConsumer;
import com.example.filestorage.production.outbox.ProcessedEventRepository;
import com.example.filestorage.production.ops.StorageReconciliationService;
import com.example.filestorage.production.processing.FileProcessingJobRepository;
import com.example.filestorage.production.processing.FileProcessingJobStatus;
import com.example.filestorage.production.processing.ProcessingJobClaims;
import com.example.filestorage.production.processing.ProcessingJobExecutor;
import com.example.filestorage.sharing.CreateShareRequest;
import com.example.filestorage.sharing.PermissionRole;
import com.example.filestorage.sharing.ResourceType;
import com.example.filestorage.sharing.SharingService;
import com.example.filestorage.storage.StorageService;
import com.example.filestorage.sync.ChangeLogService;
import com.example.filestorage.upload.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Przekrojowy dowod: quota, MinIO, saga, metadane, outbox, kwarantanna i AV
 * wspoldzialaja na prawdziwej bazie oraz S3-compatible storage.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.production.antivirus.enabled=false",
        "app.production.events.adapter=logging",
        "app.production.events.consumer-enabled=false",
        "app.production.workers.enabled=false",
        "app.production.workers.processing-fixed-delay-ms=3600000",
        "app.production.outbox.publish-fixed-delay-ms=3600000"
})
class UploadSagaInfrastructureTest {
    private static final String ACCESS = "minioadmin";
    private static final String SECRET = "minioadmin123";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2024-10-13T13-34-11Z"))
            .withEnv("MINIO_ROOT_USER", ACCESS)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.storage.endpoint", UploadSagaInfrastructureTest::minioEndpoint);
        registry.add("app.storage.public-endpoint", UploadSagaInfrastructureTest::minioEndpoint);
        registry.add("app.storage.access-key", () -> ACCESS);
        registry.add("app.storage.secret-key", () -> SECRET);
        registry.add("app.storage.bucket", () -> "upload-saga-test");
    }

    private static String minioEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    @Autowired UserRepository users;
    @Autowired UploadSessionRepository sessions;
    @Autowired UploadChunkRepository chunks;
    @Autowired StorageService storage;
    @Autowired UploadFinalizationCoordinator coordinator;
    @Autowired FileMetadataRepository files;
    @Autowired FileProcessingJobRepository jobs;
    @Autowired ProcessingJobClaims claims;
    @Autowired ProcessingJobExecutor executor;
    @Autowired OutboxEventRepository outbox;
    @Autowired SharingService sharing;
    @Autowired ChangeLogService changes;
    @Autowired StorageReconciliationService reconciliation;
    @Autowired IdempotentFileEventConsumer eventConsumer;
    @Autowired ProcessedEventRepository processedEvents;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @Test
    void finalizationPublishesOnlyAfterAntivirusGate() throws Exception {
        byte[] content = "verified end-to-end payload".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        AppUser user = new AppUser("saga@example.test", "hash", "Saga learner");
        user.reserveUpload(content.length);
        users.save(user);

        UploadSession session = new UploadSession(user.getId(), null, "payload.txt", "text/plain",
                content.length, content.length, 1, hash, Instant.now().plus(1, ChronoUnit.HOURS));
        session = sessions.save(session);
        String chunkKey = "uploads/%s/0".formatted(session.getId());
        storage.put(chunkKey, new ByteArrayInputStream(content), content.length, "text/plain");
        chunks.save(new UploadChunk(session.getId(), 0, chunkKey, hash, content.length));
        session.markChunkUploaded();
        sessions.save(session);

        var response = coordinator.complete(user.getId(), session.getId());

        assertThat(response.status()).isEqualTo(FileStatus.QUARANTINED);
        assertThat(storage.exists("users/%s/blobs/%s".formatted(user.getId(), session.getId()))).isTrue();
        assertThat(users.findById(user.getId()).orElseThrow().getStorageReservedBytes()).isZero();
        assertThat(users.findById(user.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(content.length);
        assertThat(outbox.count()).isEqualTo(1);

        var claimed = claims.claim(10);
        assertThat(claimed).hasSize(1);
        executor.execute(claimed.getFirst());

        assertThat(files.findById(response.id()).orElseThrow().getStatus()).isEqualTo(FileStatus.ACTIVE);
        assertThat(jobs.countByStatus(FileProcessingJobStatus.COMPLETED)).isEqualTo(1);
        assertThat(jobs.countByStatus(FileProcessingJobStatus.PENDING)).isEqualTo(2);
        assertThat(outbox.count()).isEqualTo(2);
    }

    @Test
    void sharedRecipientReceivesMutationAndRevocationInMonotonicSyncStream() {
        AppUser owner = users.save(new AppUser("owner-sync@example.test", "hash", "Owner"));
        AppUser recipient = users.save(new AppUser("recipient-sync@example.test", "hash", "Recipient"));
        var file = files.save(new com.example.filestorage.file.FileMetadata(
                owner.getId(), null, "shared.txt", "shared.txt", "text/plain", 1,
                "objects/shared-" + java.util.UUID.randomUUID(), "hash"));

        var permission = sharing.shareFile(owner.getId(), file.getId(),
                new CreateShareRequest(recipient.getEmail(), PermissionRole.EDITOR, null));
        changes.record(owner.getId(), owner.getId(), ResourceType.FILE, file.getId(),
                "FILE_RENAMED", "{\"name\":\"renamed.txt\"}");

        var beforeRevocation = changes.changes(recipient.getId(), 0, 100);
        assertThat(beforeRevocation.changes()).extracting(change -> change.operation())
                .contains("FILE_SHARED", "FILE_RENAMED");

        long cursor = beforeRevocation.nextCursor();
        sharing.revokePermission(owner.getId(), permission.id());

        assertThat(changes.changes(recipient.getId(), cursor, 100).changes())
                .extracting(change -> change.operation())
                .containsExactly("PERMISSION_REVOKED");
    }

    @Test
    void reverseReconciliationReportsButDoesNotDeleteOldUnreferencedObject() {
        String key = "reconciliation/orphan-" + UUID.randomUUID();
        storage.put(key, new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "application/octet-stream");

        var result = reconciliation.checkStorageInventory("reconciliation/", 100, Duration.ZERO);

        assertThat(result.orphanObjects()).isGreaterThanOrEqualTo(1);
        assertThat(storage.exists(key)).isTrue();
    }

    @Test
    void inboxMarkerMakesBrokerRedeliveryIdempotentInPostgres() {
        UUID eventId = UUID.randomUUID();
        var record = new ConsumerRecord<String, String>("file-events", 0, 0, "file:1", "{}");
        record.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));

        eventConsumer.consume(record);
        eventConsumer.consume(record);

        assertThat(processedEvents.findById(eventId)).isPresent();
        assertThat(processedEvents.count()).isEqualTo(1);
    }

    @Test
    void generatedOpenApiKeepsCriticalUploadAndDownloadOperations() throws Exception {
        var response = http.getForEntity("http://localhost:" + port + "/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var paths = json.readTree(response.getBody()).path("paths");
        assertThat(paths.has("/api/v1/uploads/{uploadId}/complete")).isTrue();
        assertThat(paths.has("/api/v1/files/{fileId}/download")).isTrue();
        assertThat(json.readTree(response.getBody()).path("components").path("securitySchemes")
                .has("bearerAuth")).isTrue();
    }
}
