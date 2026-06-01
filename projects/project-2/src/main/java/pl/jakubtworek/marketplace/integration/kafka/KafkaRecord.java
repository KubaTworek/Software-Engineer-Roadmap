package pl.jakubtworek.marketplace.integration.kafka;

public record KafkaRecord(
        String topic,
        String key,
        IntegrationEventEnvelope envelope,
        long offset
) {
}
