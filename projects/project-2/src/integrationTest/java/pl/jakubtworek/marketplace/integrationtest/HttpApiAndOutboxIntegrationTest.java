package pl.jakubtworek.marketplace.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HttpApiAndOutboxIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    OutboxEventRepository outboxRepository;

    @Test
    void createsProductAddsStockAndPlacesOrderThroughHttpApiWhilePersistingOrderPlacedInOutbox() {
        UUID productId = createProduct();
        addStock(productId, 10);

        UUID customerId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Map<String, Object> request = Map.of(
                "customerId", customerId.toString(),
                "correlationId", correlationId.toString(),
                "lines", List.of(Map.of(
                        "productId", productId.toString(),
                        "quantity", 2,
                        "unitAmount", "49.99",
                        "currency", "PLN"
                ))
        );

        ResponseEntity<Map> orderResponse = rest.postForEntity(url("/api/orders"), request, Map.class);

        assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orderId = UUID.fromString(orderResponse.getBody().get("id").toString());

        ResponseEntity<Map> loadedOrder = rest.getForEntity(url("/api/orders/" + orderId), Map.class);
        assertThat(loadedOrder.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loadedOrder.getBody().get("status")).isEqualTo("PENDING");

        var outboxEvents = outboxRepository.findByStatus(OutboxEventStatus.NEW, 20);
        assertThat(outboxEvents)
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("OrderPlaced");
                    assertThat(event.aggregateId()).isEqualTo(orderId);
                    assertThat(event.correlationId()).isEqualTo(correlationId);
                    assertThat(event.payload()).contains(productId.toString());
                });

        ResponseEntity<List> adminOutbox = rest.getForEntity(url("/admin/outbox?status=NEW"), List.class);
        assertThat(adminOutbox.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminOutbox.getBody()).isNotEmpty();
    }

    private UUID createProduct() {
        ResponseEntity<Map> response = rest.postForEntity(url("/api/products"), Map.of(
                "name", "Integration Test Product",
                "amount", "49.99",
                "currency", "PLN"
        ), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private void addStock(UUID productId, int quantity) {
        ResponseEntity<Void> response = rest.postForEntity(url("/api/stock"), Map.of(
                "productId", productId.toString(),
                "quantity", quantity
        ), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
