package pl.jakubtworek.marketplace.ordering.application;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.ordering.domain.OrderStatus;
import pl.jakubtworek.marketplace.ordering.infrastructure.InMemoryOrderRepository;
import pl.jakubtworek.marketplace.shared.kernel.Money;
import pl.jakubtworek.marketplace.testsupport.RecordingEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceOrderUseCaseTest {

    @Test
    void shouldPlaceOrderPersistItPublishEventAndClearDomainEvents() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        PlaceOrderUseCase useCase = new PlaceOrderUseCase(repository, eventPublisher);
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        var orderId = useCase.handle(new PlaceOrderUseCase.Command(
                customerId,
                List.of(new PlaceOrderUseCase.Line(productId, 2, "15.00", "PLN")),
                correlationId
        ));

        var order = repository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.customerId().value()).isEqualTo(customerId);
        assertThat(order.total()).isEqualTo(Money.of("30.00", "PLN"));
        assertThat(order.domainEvents()).isEmpty();

        assertThat(eventPublisher.events()).hasSize(1);
        assertThat(eventPublisher.events().getFirst()).isInstanceOf(OrderPlaced.class);
        OrderPlaced event = eventPublisher.eventsOfType(OrderPlaced.class).getFirst();
        assertThat(event.aggregateId()).isEqualTo(orderId.value());
        assertThat(event.correlationId()).isEqualTo(correlationId);
    }
}
