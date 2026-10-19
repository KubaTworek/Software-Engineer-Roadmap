package pl.jakubtworek.backend.reservation.infrastructure;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wykonywalny dowód na prawdziwym PostgreSQL, nie na H2 ani mocku repozytorium.
 * Wszystkie próby startują współbieżnie, ale warunek atomowego UPDATE pozwala sprzedać
 * dokładnie tyle miejsc, ile istnieje. Sukces HTTP każdego workera nie jest założeniem —
 * po zakończeniu sprawdzamy historię wyników oraz końcowy niezmiennik w bazie.
 */
@Tag("infrastructure")
@Testcontainers
class PostgreSqlInventoryConcurrencyTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void concurrentReservationsNeverMakeInventoryNegative() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE event_inventory(event_id UUID PRIMARY KEY, total_tickets INT NOT NULL, available_tickets INT NOT NULL CHECK (available_tickets >= 0))");
            statement.execute("INSERT INTO event_inventory VALUES ('11111111-1111-1111-1111-111111111111', 10, 10)");
        }

        var results = new ArrayList<Future<Integer>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int attempt = 0; attempt < 32; attempt++) {
                results.add(executor.submit(() -> {
                    try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                         var statement = connection.prepareStatement("UPDATE event_inventory SET available_tickets=available_tickets-1 WHERE event_id='11111111-1111-1111-1111-111111111111' AND available_tickets >= 1")) {
                        return statement.executeUpdate();
                    }
                }));
            }
        }

        int accepted = 0;
        for (Future<Integer> result : results) accepted += result.get();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT available_tickets FROM event_inventory")) {
            rows.next();
            assertThat(accepted).isEqualTo(10);
            assertThat(rows.getInt(1)).isZero();
        }
    }

    @Test
    void staleLeaseOwnerCannotWriteAfterNewerFencingTokenWasAccepted() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE fencing_inventory(event_id UUID PRIMARY KEY, available_tickets INT NOT NULL, last_fencing_token BIGINT NOT NULL)");
            statement.execute("INSERT INTO fencing_inventory VALUES ('22222222-2222-2222-2222-222222222222', 0, 0)");
        }

        String guardedWrite = "UPDATE fencing_inventory SET available_tickets=available_tickets+1, "
                + "last_fencing_token=? WHERE event_id='22222222-2222-2222-2222-222222222222' "
                + "AND last_fencing_token <= ?";
        int newerOwner;
        int staleOwner;
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var update = connection.prepareStatement(guardedWrite)) {
            update.setLong(1, 2);
            update.setLong(2, 2);
            newerOwner = update.executeUpdate();
            /* Worker z tokenem 1 mógł odzyskać CPU już po wygaśnięciu własnego lease.
             * Sam lease go nie zatrzyma; dopiero porównanie tokenu przy chronionym zasobie. */
            update.setLong(1, 1);
            update.setLong(2, 1);
            staleOwner = update.executeUpdate();
        }

        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT available_tickets, last_fencing_token FROM fencing_inventory")) {
            row.next();
            assertThat(newerOwner).isOne();
            assertThat(staleOwner).isZero();
            assertThat(row.getInt("available_tickets")).isOne();
            assertThat(row.getLong("last_fencing_token")).isEqualTo(2);
        }
    }

    @Test
    void repeatedCatalogBootstrapNeverResetsLocallyConsumedInventory() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bootstrap_inventory(event_id UUID PRIMARY KEY, total_tickets INT NOT NULL, available_tickets INT NOT NULL)");
            statement.execute("INSERT INTO bootstrap_inventory VALUES ('33333333-3333-3333-3333-333333333333', 10, 10)");
            statement.execute("UPDATE bootstrap_inventory SET available_tickets=7 WHERE event_id='33333333-3333-3333-3333-333333333333'");
            statement.execute("INSERT INTO bootstrap_inventory VALUES ('33333333-3333-3333-3333-333333333333', 10, 10) ON CONFLICT (event_id) DO NOTHING");
            try (var row = statement.executeQuery("SELECT available_tickets FROM bootstrap_inventory")) {
                row.next();
                assertThat(row.getInt(1)).isEqualTo(7);
            }
        }
    }

    @Test
    void capacityChangePreservesReservationsAndRejectsStaleOrImpossibleCommand() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE capacity_inventory(event_id UUID PRIMARY KEY, total_tickets INT NOT NULL, available_tickets INT NOT NULL, version BIGINT NOT NULL)");
            statement.execute("INSERT INTO capacity_inventory VALUES ('44444444-4444-4444-4444-444444444444', 10, 6, 0)");
            String update = "UPDATE capacity_inventory SET available_tickets=available_tickets+(?-total_tickets), total_tickets=?, version=version+1 "
                    + "WHERE event_id='44444444-4444-4444-4444-444444444444' AND version=? AND ? >= total_tickets-available_tickets";
            try (var command = connection.prepareStatement(update)) {
                command.setInt(1, 12); command.setInt(2, 12); command.setLong(3, 0); command.setInt(4, 12);
                assertThat(command.executeUpdate()).isOne();
                command.setInt(1, 14); command.setInt(2, 14); command.setLong(3, 0); command.setInt(4, 14);
                assertThat(command.executeUpdate()).as("stale expectedVersion").isZero();
                command.setInt(1, 3); command.setInt(2, 3); command.setLong(3, 1); command.setInt(4, 3);
                assertThat(command.executeUpdate()).as("four tickets are already reserved").isZero();
            }
            try (var row = statement.executeQuery("SELECT total_tickets, available_tickets, version FROM capacity_inventory")) {
                row.next();
                assertThat(row.getInt("total_tickets")).isEqualTo(12);
                assertThat(row.getInt("available_tickets")).isEqualTo(8);
                assertThat(row.getLong("version")).isOne();
            }
        }
    }

    @Test
    void secondWorkerTakesExpiredLeaseAndFencesPausedFirstWorker() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE lease_scenario(name VARCHAR PRIMARY KEY, owner_id VARCHAR NOT NULL, lease_until TIMESTAMPTZ NOT NULL, token BIGINT NOT NULL)");
            statement.execute("CREATE TABLE lease_protected_resource(id INT PRIMARY KEY, writes INT NOT NULL, last_token BIGINT NOT NULL)");
            statement.execute("INSERT INTO lease_scenario VALUES ('expiry', 'worker-a', '2026-01-01T00:00:10Z', 1)");
            statement.execute("INSERT INTO lease_protected_resource VALUES (1, 0, 0)");

            String acquire = "UPDATE lease_scenario SET owner_id=?, lease_until=?::timestamptz, token=token+1 "
                    + "WHERE name='expiry' AND lease_until < ?::timestamptz RETURNING token";
            try (var beforeExpiry = connection.prepareStatement(acquire)) {
                beforeExpiry.setString(1, "worker-b");
                beforeExpiry.setString(2, "2026-01-01T00:00:15Z");
                beforeExpiry.setString(3, "2026-01-01T00:00:05Z");
                assertThat(beforeExpiry.executeQuery().next()).isFalse();
            }

            long tokenB;
            try (var afterExpiry = connection.prepareStatement(acquire)) {
                afterExpiry.setString(1, "worker-b");
                afterExpiry.setString(2, "2026-01-01T00:00:30Z");
                afterExpiry.setString(3, "2026-01-01T00:00:11Z");
                try (var row = afterExpiry.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    tokenB = row.getLong(1);
                }
            }

            String guarded = "UPDATE lease_protected_resource SET writes=writes+1,last_token=? WHERE id=1 AND last_token<=?";
            try (var write = connection.prepareStatement(guarded)) {
                write.setLong(1, tokenB); write.setLong(2, tokenB);
                assertThat(write.executeUpdate()).as("new owner writes").isOne();
                write.setLong(1, 1); write.setLong(2, 1);
                assertThat(write.executeUpdate()).as("paused old owner resumes too late").isZero();
            }
            try (var row = statement.executeQuery("SELECT writes,last_token FROM lease_protected_resource")) {
                row.next();
                assertThat(row.getInt("writes")).isOne();
                assertThat(row.getLong("last_token")).isEqualTo(2);
            }
        }
    }
}
