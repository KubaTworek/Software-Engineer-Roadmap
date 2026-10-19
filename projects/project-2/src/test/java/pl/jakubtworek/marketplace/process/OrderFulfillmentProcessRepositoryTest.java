package pl.jakubtworek.marketplace.process;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderFulfillmentProcessRepositoryTest {
    @Test
    void rejectsStaleSnapshotInsteadOfLosingAConcurrentFact() {
        var repository = new InMemoryOrderFulfillmentProcessRepository();
        UUID orderId = UUID.randomUUID();
        repository.save(new OrderFulfillmentProcess(orderId, Instant.now().plusSeconds(60)));

        var paymentConsumerSnapshot = repository.findByOrderId(orderId).orElseThrow();
        var inventoryConsumerSnapshot = repository.findByOrderId(orderId).orElseThrow();
        paymentConsumerSnapshot.paymentReserved();
        repository.save(paymentConsumerSnapshot);

        inventoryConsumerSnapshot.stockReserved();
        assertThatThrownBy(() -> repository.save(inventoryConsumerSnapshot))
                .isInstanceOf(ConcurrentProcessModificationException.class);

        assertThat(repository.findByOrderId(orderId).orElseThrow().completedSteps())
                .containsExactly(OrderFulfillmentProcess.Step.PAYMENT_RESERVED);
    }
}
