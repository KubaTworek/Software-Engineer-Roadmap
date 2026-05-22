package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.messaging;

import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.out.OrderEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.event.OrderPlaced;

// Outbound messaging adapter.
// It implements the application port using Kafka.
public final class KafkaOrderEventPublisherAdapter implements OrderEventPublisher {

    private final KafkaClient kafkaClient;
    private final OrderEventMessageMapper mapper;

    public KafkaOrderEventPublisherAdapter(
            KafkaClient kafkaClient,
            OrderEventMessageMapper mapper
    ) {
        this.kafkaClient = kafkaClient;
        this.mapper = mapper;
    }

    @Override
    public void publish(OrderPlaced event) {
        kafkaClient.send(
                "order-events",
                mapper.toMessage(event)
        );
    }
}