package pl.jakubtworek.marketplace.ordering.application;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.ordering.domain.*;
import pl.jakubtworek.marketplace.ordering.infrastructure.InMemoryOrderRepository;
import pl.jakubtworek.marketplace.shared.kernel.Money;
import pl.jakubtworek.marketplace.testsupport.RecordingEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CancelOrderUseCaseTest {

    @Test
    void shouldCancelPersistedOrderPublishEventAndClearDomainEvents() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        CancelOrderUseCase useCase = new CancelOrderUseCase(repository, eventPublisher);
        Order order = Order.place(
                CustomerId.of(UUID.randomUUID()),
                List.of(new OrderLine(ProductId.newId(), 1, Money.of("10.00", "PLN"))),
                UUID.randomUUID()
        );
        order.clearDomainEvents();
        repository.save(order);
        UUID correlationId = UUID.randomUUID();

        useCase.handle(order.id().value(), correlationId);

        var cancelled = repository.findById(order.id()).orElseThrow();
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.domainEvents()).isEmpty();

        assertThat(eventPublisher.events()).hasSize(1);
        assertThat(eventPublisher.events().getFirst()).isInstanceOf(OrderCancelled.class);
        OrderCancelled event = eventPublisher.eventsOfType(OrderCancelled.class).getFirst();
        assertThat(event.aggregateId()).isEqualTo(order.id().value());
        assertThat(event.correlationId()).isEqualTo(correlationId);
    }
}
