package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.kafka.IntegrationEventEnvelope;
import pl.jakubtworek.marketplace.integration.kafka.KafkaMessagePublisher;

@Profile("kafka")
@Component
public class SpringKafkaMessagePublisher implements KafkaMessagePublisher {
    private final KafkaTemplate<String, IntegrationEventEnvelope> kafkaTemplate;

    public SpringKafkaMessagePublisher(KafkaTemplate<String, IntegrationEventEnvelope> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(String topic, String key, IntegrationEventEnvelope envelope) {
        kafkaTemplate.send(topic, key, envelope).join();
    }
}
