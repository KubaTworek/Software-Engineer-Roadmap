package com.example.newsfeed.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ten test istnieje osobno od szybkiej suity, ponieważ sprawdza semantykę
 * PostgreSQL: migracje, częściowe indeksy i ON CONFLICT, których H2 nie gwarantuje.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlFeedGuaranteesInfrastructureIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void everyProjectionCanClaimTheSameEventButCannotClaimItTwice() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                // Jawna lokalizacja chroni test uruchamiany przez Failsafe przed
                // różnicami w classloaderze pluginu i naprawdę waliduje pliki repozytorium.
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();

        UUID eventId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(claim(connection, "fanout", eventId)).isEqualTo(1);
            assertThat(claim(connection, "fanout", eventId)).isZero();
            assertThat(claim(connection, "search-index", eventId)).isEqualTo(1);
        }
    }

    private int claim(java.sql.Connection connection, String consumer, UUID eventId) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO processed_kafka_events(consumer_name, event_id, event_type, processed_at)
                VALUES (?, ?, 'post.created', now())
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """)) {
            statement.setString(1, consumer);
            statement.setObject(2, eventId);
            return statement.executeUpdate();
        }
    }
}
