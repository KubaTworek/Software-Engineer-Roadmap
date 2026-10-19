package pl.jakubtworek.backend_engineering.stage_1.block_d.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("infrastructure")
@Testcontainers
class PostgreSqlConcurrencyContainerTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void resetSchema() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS outbox, customer_order, job, account");
            statement.execute("""
                    CREATE TABLE account (
                        id BIGINT PRIMARY KEY,
                        balance INTEGER NOT NULL CHECK (balance >= 0)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE job (
                        id BIGINT PRIMARY KEY,
                        status VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE customer_order (
                        id BIGINT PRIMARY KEY,
                        status VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE outbox (
                        id BIGINT PRIMARY KEY,
                        aggregate_id BIGINT NOT NULL,
                        payload TEXT NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO account (id, balance) VALUES (1, 100)");
            statement.execute("INSERT INTO job (id, status) VALUES (1, 'READY'), (2, 'READY')");
        }
    }

    @Test
    void rowLockBlocksConcurrentUpdateUntilOwningTransactionFinishes() throws SQLException {
        try (Connection lockOwner = openConnection();
             Connection contender = openConnection()) {
            lockOwner.setAutoCommit(false);
            contender.setAutoCommit(false);

            try (Statement statement = lockOwner.createStatement()) {
                statement.executeQuery("SELECT balance FROM account WHERE id = 1 FOR UPDATE").close();
                statement.executeUpdate("UPDATE account SET balance = 90 WHERE id = 1");
            }
            try (Statement statement = contender.createStatement()) {
                statement.execute("SET LOCAL lock_timeout = '250ms'");
                SQLException timeout;
                try {
                    statement.executeUpdate("UPDATE account SET balance = balance + 5 WHERE id = 1");
                    throw new AssertionError("concurrent update should time out while the row is locked");
                } catch (SQLException expected) {
                    timeout = expected;
                }

                assertThat(timeout.getSQLState()).isEqualTo("55P03");
            }

            contender.rollback();
            lockOwner.commit();

            try (Statement statement = contender.createStatement()) {
                statement.executeUpdate("UPDATE account SET balance = balance + 5 WHERE id = 1");
            }
            contender.commit();
        }

        assertThat(queryForLong("SELECT balance FROM account WHERE id = 1")).isEqualTo(95);
    }

    @Test
    void skipLockedLetsAnotherWorkerClaimAFreeRow() throws SQLException {
        try (Connection firstWorker = openConnection();
             Connection secondWorker = openConnection()) {
            firstWorker.setAutoCommit(false);
            secondWorker.setAutoCommit(false);

            assertThat(claimNextJob(firstWorker, false)).isEqualTo(1);
            assertThat(claimNextJob(secondWorker, true)).isEqualTo(2);

            firstWorker.rollback();
            secondWorker.rollback();
        }
    }

    @Test
    void businessChangeAndOutboxRecordCommitOrRollbackTogether() throws SQLException {
        writeOrderAndOutbox(false);
        assertThat(queryForLong("SELECT count(*) FROM customer_order")).isZero();
        assertThat(queryForLong("SELECT count(*) FROM outbox")).isZero();

        writeOrderAndOutbox(true);
        assertThat(queryForLong("SELECT count(*) FROM customer_order")).isOne();
        assertThat(queryForLong("SELECT count(*) FROM outbox")).isOne();
    }

    private long claimNextJob(Connection connection, boolean skipLocked) throws SQLException {
        String suffix = skipLocked ? " SKIP LOCKED" : "";
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                     "SELECT id FROM job WHERE status = 'READY' ORDER BY id LIMIT 1 FOR UPDATE" + suffix
             )) {
            assertThat(result.next()).isTrue();
            return result.getLong("id");
        }
    }

    private void writeOrderAndOutbox(boolean commit) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO customer_order (id, status) VALUES (101, 'CREATED')");
            statement.executeUpdate("""
                    INSERT INTO outbox (id, aggregate_id, payload)
                    VALUES (1001, 101, '{"type":"OrderCreated"}')
                    """);
            if (commit) {
                connection.commit();
            } else {
                connection.rollback();
            }
        }
    }

    private long queryForLong(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }
}
