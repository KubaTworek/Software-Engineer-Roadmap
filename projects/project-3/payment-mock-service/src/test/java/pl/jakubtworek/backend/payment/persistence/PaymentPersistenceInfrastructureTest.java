package pl.jakubtworek.backend.payment.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("infrastructure")
@Testcontainers
class PaymentPersistenceInfrastructureTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void paymentSurvivesConnectionRestartAndOperationLockIsSharedByReplicas() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        try (var firstProcess = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var insert = firstProcess.prepareStatement("INSERT INTO payments VALUES (?, ?, ?, 'user', 10.00, 'PAID', NULL, now(), NULL)")) {
            insert.setString(1, "stable-key");
            insert.setObject(2, paymentId);
            insert.setObject(3, orderId);
            insert.executeUpdate();
        }
        /* Nowe połączenie reprezentuje restart procesu: wynik nie zależy już od pamięci JVM. */
        try (var restartedProcess = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var query = restartedProcess.prepareStatement("SELECT payment_id, status FROM payments WHERE order_id=?")) {
            query.setObject(1, orderId);
            try (var row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject("payment_id", UUID.class)).isEqualTo(paymentId);
                assertThat(row.getString("status")).isEqualTo("PAID");
            }
        }

        try (var ownerA = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var ownerB = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            ownerA.setAutoCommit(false);
            ownerB.setAutoCommit(false);
            ownerA.createStatement().execute("SELECT pg_advisory_xact_lock(hashtext('payment:stable-key'))");
            try (var attempt = ownerB.createStatement().executeQuery(
                    "SELECT pg_try_advisory_xact_lock(hashtext('payment:stable-key'))")) {
                attempt.next();
                assertThat(attempt.getBoolean(1)).isFalse();
            } finally {
                ownerA.rollback();
                ownerB.rollback();
            }
        }
    }
}
