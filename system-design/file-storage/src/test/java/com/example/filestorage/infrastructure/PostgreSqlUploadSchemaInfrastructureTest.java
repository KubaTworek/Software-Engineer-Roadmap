package com.example.filestorage.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kontrakt infrastrukturalny: PostgreSQL, a nie fake, ma atomowo odrzucić drugi
 * zapis tej samej części uploadu. To zabezpiecza licznik postępu przed retry.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlUploadSchemaInfrastructureTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void primaryKeyRejectsDuplicateChunkForTheSameUpload() throws Exception {
        migrate();

        UUID userId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        try (var connection = POSTGRES.createConnection("")) {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO app_users(id, email, password_hash, display_name, created_at, updated_at)
                    VALUES (?, ?, 'hash', 'Learner', ?, ?)
                    """)) {
                statement.setObject(1, userId);
                statement.setString(2, userId + "@example.test");
                statement.setObject(3, OffsetDateTime.now());
                statement.setObject(4, OffsetDateTime.now());
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO upload_sessions(
                        id, user_id, filename, content_type, total_size_bytes, chunk_size_bytes,
                        total_chunks, uploaded_chunks, status, created_at, updated_at, expires_at
                    ) VALUES (?, ?, 'file.bin', 'application/octet-stream', 10, 5, 2, 0,
                              'IN_PROGRESS', ?, ?, ?)
                    """)) {
                statement.setObject(1, uploadId);
                statement.setObject(2, userId);
                statement.setObject(3, OffsetDateTime.now());
                statement.setObject(4, OffsetDateTime.now());
                statement.setObject(5, OffsetDateTime.now().plusHours(1));
                statement.executeUpdate();
            }

            insertChunk(connection, uploadId, 0);
            assertThatThrownBy(() -> insertChunk(connection, uploadId, 0))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void databaseConstraintRejectsQuotaOversubscription() {
        migrate();
        assertThatThrownBy(() -> {
            try (var connection = POSTGRES.createConnection("");
                 var statement = connection.prepareStatement("""
                         INSERT INTO app_users(
                           id, email, password_hash, display_name,
                           storage_quota_bytes, storage_used_bytes, storage_reserved_bytes,
                           created_at, updated_at
                         ) VALUES (?, ?, 'hash', 'Learner', 100, 60, 41, ?, ?)
                         """)) {
                UUID id = UUID.randomUUID();
                statement.setObject(1, id);
                statement.setString(2, id + "@example.test");
                statement.setObject(3, OffsetDateTime.now());
                statement.setObject(4, OffsetDateTime.now());
                statement.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void insertChunk(java.sql.Connection connection, UUID uploadId, int index) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO upload_chunks(upload_session_id, chunk_index, object_key, sha256, size_bytes, uploaded_at)
                VALUES (?, ?, ?, 'sha256', 5, ?)
                """)) {
            statement.setObject(1, uploadId);
            statement.setInt(2, index);
            statement.setString(3, uploadId + "/" + index);
            statement.setObject(4, OffsetDateTime.now());
            statement.executeUpdate();
        }
    }
}
