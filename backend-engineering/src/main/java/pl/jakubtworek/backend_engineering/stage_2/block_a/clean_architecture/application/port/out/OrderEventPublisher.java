package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.out;

import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.event.OrderPlaced;

// Output port for publishing events.
// The application does not know whether Kafka, RabbitMQ, JMS, or outbox is used.
public interface OrderEventPublisher {

    void publish(OrderPlaced event);
}