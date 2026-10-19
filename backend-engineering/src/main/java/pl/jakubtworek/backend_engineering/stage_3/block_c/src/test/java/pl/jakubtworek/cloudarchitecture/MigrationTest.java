package pl.jakubtworek.cloudarchitecture;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationTest {

    @Test
    void createsTheCompleteSchemaFromAnEmptyDatabase() throws Exception {
        String url = "jdbc:h2:mem:cloud-architecture;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure().dataSource(url, "sa", "").load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var result = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            java.util.Set<String> tables = new java.util.HashSet<>();
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME").toLowerCase());
            }
            assertThat(tables).contains(
                    "orders",
                    "products",
                    "outbox_events",
                    "processed_order_events"
            );
        }
    }
}
