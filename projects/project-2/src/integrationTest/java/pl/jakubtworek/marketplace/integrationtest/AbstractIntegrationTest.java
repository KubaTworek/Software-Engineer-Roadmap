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
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"postgres", "kafka"})
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
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            IntegrationPostgresContainer.getInstance();

    private static final KafkaContainer KAFKA =
            IntegrationKafkaContainer.getInstance();

    private static final String RUN_ID =
            UUID.randomUUID().toString().replace("-", "");

    protected static final String ORDER_EVENTS_TOPIC =
            "marketplace.order-events." + RUN_ID;

    protected static final String PAYMENT_EVENTS_TOPIC =
            "marketplace.payment-events." + RUN_ID;

    protected static final String INVENTORY_EVENTS_TOPIC =
            "marketplace.inventory-events." + RUN_ID;

    protected static final String FULFILLMENT_EVENTS_TOPIC =
            "marketplace.fulfillment-events." + RUN_ID;

    protected static final String DLQ_TOPIC =
            "marketplace.dlq." + RUN_ID;

    protected static final String PAYMENT_CONSUMER_GROUP =
            "marketplace-payment-" + RUN_ID;

    protected static final String INVENTORY_CONSUMER_GROUP =
            "marketplace-inventory-" + RUN_ID;

    protected static final String ORDERING_CONSUMER_GROUP =
            "marketplace-ordering-" + RUN_ID;

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
    static void registerInfrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.enable-auto-commit", () -> "false");

        registry.add("marketplace.events.mode", () -> "kafka");
        registry.add("marketplace.kafka.poll-timeout-ms", () -> "1000");

        registry.add("marketplace.kafka.topics.order-events", () -> ORDER_EVENTS_TOPIC);
        registry.add("marketplace.kafka.topics.payment-events", () -> PAYMENT_EVENTS_TOPIC);
        registry.add("marketplace.kafka.topics.inventory-events", () -> INVENTORY_EVENTS_TOPIC);
        registry.add("marketplace.kafka.topics.fulfillment-events", () -> FULFILLMENT_EVENTS_TOPIC);
        registry.add("marketplace.kafka.topics.dlq", () -> DLQ_TOPIC);

        registry.add("marketplace.kafka.consumer-groups.payment", () -> PAYMENT_CONSUMER_GROUP);
        registry.add("marketplace.kafka.consumer-groups.inventory", () -> INVENTORY_CONSUMER_GROUP);
        registry.add("marketplace.kafka.consumer-groups.ordering", () -> ORDERING_CONSUMER_GROUP);

        registry.add("marketplace.kafka.retry.max-attempts", () -> "3");
        registry.add("marketplace.kafka-outbox.scheduled-enabled", () -> "false");
    }

    @BeforeEach
    void cleanDatabase() {
        cleanIntegrationTables();
        cleanDomainTablesIfExist();
    }

    protected void cleanIntegrationTables() {
        truncateIfExists("integration", "dead_letter_events");
        truncateIfExists("integration", "processed_events");
        truncateIfExists("integration", "outbox_events");
    }

    protected void cleanDomainTablesIfExist() {
        truncateIfExists("ordering", "order_lines");
        truncateIfExists("ordering", "orders");

        truncateIfExists("catalog", "products");

        truncateIfExists("payment", "payments");

        truncateIfExists("inventory", "stock_reservations");
        truncateIfExists("inventory", "stock_items");

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

        if (exists) {
            jdbcTemplate.execute(
                    "TRUNCATE TABLE " + schemaName + "." + tableName + " RESTART IDENTITY CASCADE"
            );
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

    protected String kafkaBootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}