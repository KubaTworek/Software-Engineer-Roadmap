package pl.jakubtworek.marketplace.integration.kafka;

/**
 * Techniczny rekord pobrany z Kafki.
 *
 * W prawdziwej Kafce offset jest unikalny tylko w ramach konkretnej partycji,
 * dlatego rekord powinien zawierać topic, partition i offset.
 */
public record KafkaRecord(
        String topic,
        int partition,
        String key,
        IntegrationEventEnvelope envelope,
        long offset
) {
}