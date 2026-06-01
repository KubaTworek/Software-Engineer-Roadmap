package pl.jakubtworek.marketplace.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres")
@TestPropertySource(properties = {
        "marketplace.kafka-outbox.scheduled-enabled=false",

        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",

        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.max-lifetime=30000",
        "spring.datasource.hikari.idle-timeout=10000",
        "spring.datasource.hikari.connection-timeout=10000"
})
public abstract class AbstractPostgresIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            IntegrationPostgresContainer.getInstance();

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TestRestTemplate rest;

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeEach
    void cleanDatabase() {
        cleanIntegrationTables();
        cleanDomainTablesIfExist();
    }

    protected void cleanIntegrationTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    integration.dead_letter_events,
                    integration.processed_events,
                    integration.outbox_events
                RESTART IDENTITY CASCADE
                """);
    }

    protected void cleanDomainTablesIfExist() {
        truncateIfExists("ordering", "orders");
        truncateIfExists("ordering", "order_lines");
        truncateIfExists("catalog", "products");
        truncateIfExists("payment", "payments");
        truncateIfExists("inventory", "stock_items");
        truncateIfExists("inventory", "stock_reservations");
        truncateIfExists("fulfillment", "shipments");
        truncateIfExists("fulfillment", "fulfillment_orders");
        truncateIfExists("notification", "notifications");
    }

    protected void truncateIfExists(String schemaName, String tableName) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.tables
                            WHERE table_schema = ?
                              AND table_name = ?
                        )
                        """,
                Boolean.class,
                schemaName,
                tableName
        );

        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute("TRUNCATE TABLE " + schemaName + "." + tableName + " RESTART IDENTITY CASCADE");
        }
    }

    protected Integer countRows(String schemaName, String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + "." + tableName,
                Integer.class
        );
    }

    protected Integer countOutboxEvents() {
        return countRows("integration", "outbox_events");
    }

    protected Integer countProcessedEvents() {
        return countRows("integration", "processed_events");
    }

    protected Integer countDlqEvents() {
        return countRows("integration", "dead_letter_events");
    }

    protected String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    protected String postgresHost() {
        return POSTGRES.getHost();
    }

    protected Integer postgresPort() {
        return POSTGRES.getMappedPort(5432);
    }
}