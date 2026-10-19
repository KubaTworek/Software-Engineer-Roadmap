package pl.jakubtworek.marketplace.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test przekrojowy nie wywołuje KafkaConsumerWorker ręcznie. HTTP zapisuje Order
 * i outbox, scheduler publikuje, a prawdziwe kontenery @KafkaListener prowadzą
 * przepływ przez Payment, Inventory, Ordering, sagę i Fulfillment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles({"postgres", "kafka"})
class MarketplaceKafkaListenerRuntimeIntegrationTest {
    private static final String RUN = UUID.randomUUID().toString().replace("-", "");

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        var postgres = IntegrationPostgresContainer.getInstance();
        var kafka = IntegrationKafkaContainer.getInstance();
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // Test publikuje zaraz po starcie kontekstu; earliest usuwa wyścig z pierwszym rebalance.
        properties.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        properties.add("spring.kafka.consumer.enable-auto-commit", () -> "false");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        properties.add("marketplace.kafka.topics.order-events", () -> "runtime.order." + RUN);
        properties.add("marketplace.kafka.topics.payment-events", () -> "runtime.payment." + RUN);
        properties.add("marketplace.kafka.topics.inventory-events", () -> "runtime.inventory." + RUN);
        properties.add("marketplace.kafka.topics.fulfillment-events", () -> "runtime.fulfillment." + RUN);
        properties.add("marketplace.kafka.consumer-groups.payment", () -> "runtime-payment-" + RUN);
        properties.add("marketplace.kafka.consumer-groups.inventory", () -> "runtime-inventory-" + RUN);
        properties.add("marketplace.kafka.consumer-groups.ordering", () -> "runtime-ordering-" + RUN);
        properties.add("marketplace.kafka.consumer-groups.process", () -> "runtime-process-" + RUN);
        properties.add("marketplace.kafka.consumer-groups.fulfillment", () -> "runtime-fulfillment-" + RUN);
        properties.add("marketplace.kafka.consumer-groups.notification", () -> "runtime-notification-" + RUN);
        properties.add("marketplace.kafka.consumers.enabled", () -> "true");
        properties.add("marketplace.kafka-outbox.scheduled-enabled", () -> "true");
        properties.add("marketplace.kafka-outbox.worker-delay-ms", () -> "50");
        properties.add("marketplace.fulfillment-process.timeout-worker-enabled", () -> "false");
    }

    @Test
    void completesTheOrderThroughTheRealKafkaListenerRuntime() {
        UUID productId = createProduct();
        addStock(productId);
        UUID orderId = placeOrder(productId);

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            Map order = rest.getForObject(url("/api/orders/" + orderId), Map.class);
            assertThat(order.get("status")).isEqualTo("CONFIRMED");
            assertThat(count("fulfillment.shipments", orderId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM integration.order_fulfillment_processes WHERE order_id = ?
                    """, String.class, orderId)).isEqualTo("CONFIRMED");
        });

        // Kafka topic jest współdzielony przez klasy tej suite; dowodzimy, że DLQ nie
        // zawiera zdarzenia bieżącego flow, zamiast zakładać globalnie pusty broker.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration.dead_letter_events WHERE envelope::text LIKE ?",
                        Integer.class, "%" + orderId + "%"))
                .isZero();
    }

    @Test
    void executesCompensationAndClosesSagaThroughTheRealKafkaRuntime() {
        UUID productId = createProduct();
        // Celowo nie dodajemy stocku: Payment może zdążyć się zarezerwować,
        // a Inventory zwróci porażkę i saga musi wysłać osobną komendę zwolnienia.
        UUID orderId = placeOrder(productId);

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            Map order = rest.getForObject(url("/api/orders/" + orderId), Map.class);
            assertThat(order.get("status")).isEqualTo("REJECTED");
            assertThat(jdbc.queryForList("SELECT status FROM payment.payments WHERE order_id = ?",
                    String.class, orderId)).containsExactly("REFUNDED");
            assertThat(jdbc.queryForList("""
                    SELECT status FROM integration.order_fulfillment_processes WHERE order_id = ?
                    """, String.class, orderId)).containsExactly("CANCELLED");
        });

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM integration.outbox_events
                WHERE event_type IN ('PaymentReleaseRequested', 'PaymentReleased')
                  AND payload::text LIKE ?
                """, Integer.class, "%" + orderId + "%")).isEqualTo(2);
    }

    private UUID createProduct() {
        var response = rest.postForEntity(url("/api/products"), Map.of(
                "name", "Runtime Kafka product", "amount", "49.99", "currency", "PLN"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private void addStock(UUID productId) {
        var response = rest.postForEntity(url("/api/stock"), Map.of(
                "productId", productId.toString(), "quantity", 5), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private UUID placeOrder(UUID productId) {
        var response = rest.postForEntity(url("/api/orders"), Map.of(
                "customerId", UUID.randomUUID().toString(),
                "lines", List.of(Map.of("productId", productId.toString(), "quantity", 2))), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private int count(String table, UUID orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE order_id = ?", Integer.class, orderId);
    }

    private String url(String path) { return "http://localhost:" + port + path; }
}
