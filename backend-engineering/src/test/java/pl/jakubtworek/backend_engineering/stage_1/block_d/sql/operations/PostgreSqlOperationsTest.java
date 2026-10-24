package pl.jakubtworek.backend_engineering.stage_1.block_d.sql.operations;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("infrastructure")
@Testcontainers
class PostgreSqlOperationsTest {
    @Container static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    @Test void longTransactionBlocksDdlAndLockTimeoutBoundsTheWait() throws Exception {
        try (var first = connect(); var second = connect()) {
            first.createStatement().execute("create table ddl_lab(id int primary key)");
            first.setAutoCommit(false);
            first.createStatement().executeQuery("select * from ddl_lab").close();
            second.createStatement().execute("set lock_timeout='100ms'");
            SQLException blocked = assertThrows(SQLException.class,
                    () -> second.createStatement().execute("alter table ddl_lab add column note text"));
            assertEquals("55P03", blocked.getSQLState());
            first.rollback();
            second.createStatement().execute("alter table ddl_lab add column note text");
        }
    }

    @Test void vacuumCannotRemoveVersionStillNeededByOldSnapshot() throws Exception {
        try (var snapshot = connect(); var writer = connect()) {
            writer.createStatement().execute("create table vacuum_lab(id int primary key, value int)");
            writer.createStatement().execute("insert into vacuum_lab values(1,10)");
            snapshot.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            snapshot.setAutoCommit(false);
            assertEquals(10, value(snapshot)); // Dopiero pierwszy odczyt ustala snapshot.
            writer.createStatement().execute("update vacuum_lab set value=20 where id=1");
            writer.createStatement().execute("vacuum analyze vacuum_lab");
            assertEquals(10, value(snapshot));
            snapshot.commit();
            assertEquals(20, value(snapshot));
            snapshot.commit();
        }
    }
    private int value(Connection connection) throws SQLException {
        try (var query = connection.createStatement(); var rs = query.executeQuery("select value from vacuum_lab where id=1")) {
            rs.next(); return rs.getInt(1);
        }
    }
}
