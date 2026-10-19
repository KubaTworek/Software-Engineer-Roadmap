package pl.jakubtworek.backend.order.outbox;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Weryfikuje dialekt PostgreSQL, migracje i najważniejszą własność SKIP LOCKED.
 * H2 nie jest tu zamiennikiem: składnia partial index, advisory locks i zachowanie
 * blokad wierszy są częścią kontraktu infrastrukturalnego laboratorium.
 */
@Tag("infrastructure")
@Testcontainers
class PostgreSqlOutboxInfrastructureTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void secondRelaySkipsRowLockedByFirstRelayAndPoisonColumnExists() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        UUID messageId = UUID.randomUUID();
        try (var setup = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var insert = setup.prepareStatement("INSERT INTO outbox_messages(id, aggregate_id, event_type, payload, occurred_at) VALUES (?, 'order', 'order.paid.v1', '{}', now())")) {
            insert.setObject(1, messageId);
            insert.executeUpdate();
        }

        String lockSql = "SELECT id FROM outbox_messages WHERE published_at IS NULL AND dead_at IS NULL ORDER BY occurred_at LIMIT 100 FOR UPDATE SKIP LOCKED";
        try (var first = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var second = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            try (var firstLock = first.createStatement().executeQuery(lockSql);
                 var secondLock = second.createStatement().executeQuery(lockSql)) {
                assertThat(firstLock.next()).isTrue();
                assertThat(firstLock.getObject(1, UUID.class)).isEqualTo(messageId);
                /* Druga replika nie czeka i nie publikuje tej samej wiadomości równolegle. */
                assertThat(secondLock.next()).isFalse();
            } finally {
                first.rollback();
                second.rollback();
            }
        }
    }
}
