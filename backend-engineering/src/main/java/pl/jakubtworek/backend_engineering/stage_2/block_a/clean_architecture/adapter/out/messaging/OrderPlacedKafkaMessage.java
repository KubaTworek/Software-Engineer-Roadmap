package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.messaging;

// Message DTO sent to Kafka.
// It is an infrastructure contract, not a domain event.
public record OrderPlacedKafkaMessage(
        String eventId,
        String orderId,
        String occurredAt
) {
}