package pl.jakubtworek.backend_engineering.stage_1.block_d.sql;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executable PostgreSQL specification for the SQL laboratories. The assertions
 * focus on database guarantees and work performed by a plan, not on fragile
 * wall-clock timings from one machine.
 */
@Tag("infrastructure")
@Testcontainers
class PostgreSqlExecutableLabTest {

    private static final Path SQL_ROOT = resolveSqlRoot();
    private static final Pattern ACTUAL_ROWS = Pattern.compile("\\\"Actual Rows\\\"\\s*:\\s*(\\d+)");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void resetSharedTables() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    DROP TABLE IF EXISTS lab_orders, isolation_account,
                        optimistic_account, outbox, payments, order_items,
                        orders, accounts, users CASCADE
                    """);
        }
    }

    @Test
    void runsExpandBackfillAndContractAsSeparateDeploymentSteps() throws Exception {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");

        migrateTo(schema, "1");
        executeInSchema(schema, """
                INSERT INTO customer_profile(first_name, last_name)
                VALUES ('Ada', 'Lovelace')
                """);

        migrateTo(schema, "2");
        assertThat(queryString(schema,
                "SELECT display_name FROM customer_profile WHERE id = 1"))
                .isNull();

        executeInSchema(schema, readSql("backfill/backfill_display_name.sql"));
        assertThat(queryString(schema,
                "SELECT display_name FROM customer_profile WHERE id = 1"))
                .isEqualTo("Ada Lovelace");

        migrateTo(schema, "3");

        assertThat(appliedMigrationVersions(schema)).containsExactly("1", "2", "3");
        assertThat(columnIsNullable(schema, "customer_profile", "display_name")).isFalse();
        assertThat(columnExists(schema, "customer_profile", "first_name")).isTrue();
        assertThat(columnExists(schema, "customer_profile", "last_name")).isTrue();
        assertSqlStateInSchema(schema, """
                INSERT INTO customer_profile(first_name, last_name, display_name)
                VALUES ('Grace', 'Hopper', NULL)
                """, "23502");
    }

    @Test
    void explainAnalyzeBuffersConfirmsIndexUseForSelectiveAccessPattern() throws Exception {
        createOrdersForPlans();

        String plan = explain("""
                SELECT id, customer_id, created_at
                FROM lab_orders
                WHERE customer_id = 42
                ORDER BY created_at DESC, id DESC
                LIMIT 20
                """);

        assertThat(plan).contains("Index Scan");
        assertThat(plan).contains("idx_lab_orders_customer_timeline");
        assertThat(plan).contains("Buffers:");
        assertThat(plan).contains("actual time=");
    }

    @Test
    void keysetPaginationReadsBoundedRowsWhileDeepOffsetMustWalkSkippedRows() throws Exception {
        createOrdersForPlans();

        String offsetPlan = explainJson("""
                SELECT id
                FROM lab_orders
                ORDER BY id DESC
                LIMIT 20 OFFSET 40000
                """);
        String keysetPlan = explainJson("""
                SELECT id
                FROM lab_orders
                WHERE id < 10001
                ORDER BY id DESC
                LIMIT 20
                """);

        long offsetRowsVisited = maximumActualRows(offsetPlan);
        long keysetRowsVisited = maximumActualRows(keysetPlan);

        assertThat(offsetRowsVisited).isGreaterThanOrEqualTo(40_020);
        assertThat(keysetRowsVisited).isLessThanOrEqualTo(20);
        assertThat(offsetRowsVisited).isGreaterThan(keysetRowsVisited);
    }

    @Test
    void readCommittedRefreshesSnapshotButRepeatableReadKeepsTransactionSnapshot()
            throws SQLException {
        execute("""
                CREATE TABLE isolation_account (
                    id BIGINT PRIMARY KEY,
                    balance INTEGER NOT NULL
                );
                INSERT INTO isolation_account(id, balance) VALUES (1, 100)
                """);

        try (Connection reader = openConnection()) {
            reader.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            reader.setAutoCommit(false);

            assertThat(balance(reader)).isEqualTo(100);
            updateBalance(200);
            assertThat(balance(reader)).isEqualTo(200);
            reader.rollback();
        }

        updateBalance(100);
        try (Connection reader = openConnection()) {
            reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            reader.setAutoCommit(false);

            assertThat(balance(reader)).isEqualTo(100);
            updateBalance(300);
            assertThat(balance(reader)).isEqualTo(100);
            reader.commit();
        }

        assertThat(queryLong("SELECT balance FROM isolation_account WHERE id = 1"))
                .isEqualTo(300);
    }

    @Test
    void optimisticUpdateRejectsWriterHoldingStaleVersion() throws SQLException {
        execute("""
                CREATE TABLE optimistic_account (
                    id BIGINT PRIMARY KEY,
                    balance INTEGER NOT NULL CHECK (balance >= 0),
                    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0)
                );
                INSERT INTO optimistic_account(id, balance) VALUES (1, 100)
                """);

        int firstReaderVersion = currentVersion();
        int secondReaderVersion = currentVersion();

        assertThat(updateWithVersion(90, firstReaderVersion)).isOne();
        assertThat(updateWithVersion(80, secondReaderVersion)).isZero();
        assertThat(queryLong("SELECT balance FROM optimistic_account WHERE id = 1"))
                .isEqualTo(90);
        assertThat(queryLong("SELECT version FROM optimistic_account WHERE id = 1"))
                .isOne();
    }

    @Test
    void constraintsRejectInvalidStateEvenWhenWriteBypassesApplication() throws Exception {
        execute(readSql("workload/schema.sql"));

        assertSqlState("INSERT INTO users(name) VALUES ('   ')", "23514");
        assertSqlState("INSERT INTO accounts(user_id, balance) VALUES (1, -0.01)", "23514");
        assertSqlState("INSERT INTO accounts(user_id, balance) VALUES (1, 50)", "23505");
        assertSqlState("INSERT INTO orders(user_id, status) VALUES (999, 'PENDING')", "23503");
        assertSqlState("INSERT INTO orders(user_id, status) VALUES (1, 'UNKNOWN')", "23514");
        assertSqlState("INSERT INTO order_items(order_id, product_id, quantity) VALUES (1, 999, 0)",
                "23514");
        assertSqlState("INSERT INTO order_items(order_id, product_id, quantity) VALUES (1, 100, 1)",
                "23505");

        assertThat(queryLong("SELECT count(*) FROM users")).isEqualTo(2);
        assertThat(queryLong("SELECT count(*) FROM accounts")).isEqualTo(2);
        assertThat(queryLong("SELECT count(*) FROM orders")).isEqualTo(3);
    }

    private static void migrateTo(String schema, String targetVersion) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("filesystem:" + SQL_ROOT.resolve("migration"))
                .target(MigrationVersion.fromVersion(targetVersion))
                .load()
                .migrate();
    }

    private static List<String> appliedMigrationVersions(String schema) throws SQLException {
        try (Connection connection = openConnection(schema);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT version
                     FROM flyway_schema_history
                     WHERE success AND version IS NOT NULL
                     ORDER BY installed_rank
                     """)) {
            List<String> versions = new ArrayList<>();
            while (result.next()) {
                versions.add(result.getString(1));
            }
            return versions;
        }
    }

    private static boolean columnExists(String schema, String table, String column)
            throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT EXISTS (
                         SELECT 1 FROM information_schema.columns
                         WHERE table_schema = ? AND table_name = ? AND column_name = ?
                     )
                     """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static boolean columnIsNullable(String schema, String table, String column)
            throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT is_nullable = 'YES'
                     FROM information_schema.columns
                     WHERE table_schema = ? AND table_name = ? AND column_name = ?
                     """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static void createOrdersForPlans() throws SQLException {
        execute("""
                CREATE TABLE lab_orders (
                    id BIGINT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL,
                    status TEXT NOT NULL
                );
                INSERT INTO lab_orders(id, customer_id, created_at, status)
                SELECT value,
                       value % 10000,
                       TIMESTAMPTZ '2025-01-01 00:00:00Z' + value * INTERVAL '1 second',
                       CASE WHEN value % 2 = 0 THEN 'PAID' ELSE 'PENDING' END
                FROM generate_series(1, 50000) AS value;
                CREATE INDEX idx_lab_orders_customer_timeline
                    ON lab_orders(customer_id, created_at DESC, id DESC);
                ANALYZE lab_orders
                """);
    }

    private static String explain(String query) throws SQLException {
        return queryRows("EXPLAIN (ANALYZE, BUFFERS) " + query);
    }

    private static String explainJson(String query) throws SQLException {
        return queryRows("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query);
    }

    private static String queryRows(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            StringBuilder rows = new StringBuilder();
            while (result.next()) {
                rows.append(result.getString(1)).append(System.lineSeparator());
            }
            return rows.toString();
        }
    }

    private static long maximumActualRows(String jsonPlan) {
        Matcher matcher = ACTUAL_ROWS.matcher(jsonPlan);
        long maximum = -1;
        while (matcher.find()) {
            maximum = Math.max(maximum, Long.parseLong(matcher.group(1)));
        }
        assertThat(maximum).as("maximum Actual Rows in PostgreSQL JSON plan").isNotNegative();
        return maximum;
    }

    private static int balance(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT balance FROM isolation_account WHERE id = 1")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void updateBalance(int balance) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE isolation_account SET balance = ? WHERE id = 1")) {
            statement.setInt(1, balance);
            statement.executeUpdate();
        }
    }

    private static int currentVersion() throws SQLException {
        return Math.toIntExact(queryLong(
                "SELECT version FROM optimistic_account WHERE id = 1"));
    }

    private static int updateWithVersion(int balance, int expectedVersion) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE optimistic_account
                     SET balance = ?, version = version + 1
                     WHERE id = 1 AND version = ?
                     """)) {
            statement.setInt(1, balance);
            statement.setInt(2, expectedVersion);
            return statement.executeUpdate();
        }
    }

    private static void assertSqlState(String sql, String expectedState) {
        assertThatThrownBy(() -> execute(sql))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo(expectedState);
    }

    private static void assertSqlStateInSchema(String schema, String sql, String expectedState) {
        assertThatThrownBy(() -> executeInSchema(schema, sql))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo(expectedState);
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void executeInSchema(String schema, String sql) throws SQLException {
        try (Connection connection = openConnection(schema);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long queryLong(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static String queryString(String schema, String sql) throws SQLException {
        try (Connection connection = openConnection(schema);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection openConnection(String schema) throws SQLException {
        Connection connection = openConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
        }
        return connection;
    }

    private static String readSql(String relativePath) throws IOException {
        return Files.readString(SQL_ROOT.resolve(relativePath));
    }

    private static Path resolveSqlRoot() {
        Path sourceRelativeToModule = Path.of(
                "src", "main", "java", "pl", "jakubtworek", "backend_engineering",
                "stage_1", "block_d", "sql").toAbsolutePath();
        if (Files.isDirectory(sourceRelativeToModule)) {
            return sourceRelativeToModule;
        }

        Path sourceRelativeToRepository = Path.of("backend-engineering")
                .resolve(Path.of(
                        "src", "main", "java", "pl", "jakubtworek", "backend_engineering",
                        "stage_1", "block_d", "sql"))
                .toAbsolutePath();
        if (Files.isDirectory(sourceRelativeToRepository)) {
            return sourceRelativeToRepository;
        }

        throw new IllegalStateException("Cannot locate Stage 1D SQL laboratory sources");
    }
}
