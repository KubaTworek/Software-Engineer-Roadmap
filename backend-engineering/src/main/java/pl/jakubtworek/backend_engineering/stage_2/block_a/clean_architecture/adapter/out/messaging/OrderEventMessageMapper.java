package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.messaging;

import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.event.OrderPlaced;

// Mapper between domain event and Kafka message.
// The domain event stays independent from serialization format.
public final class OrderEventMessageMapper {

    public OrderPlacedKafkaMessage toMessage(OrderPlaced event) {
        return new OrderPlacedKafkaMessage(
                event.eventId(),
                event.orderId().value(),
                event.occurredAt().toString()
        );
    }
}