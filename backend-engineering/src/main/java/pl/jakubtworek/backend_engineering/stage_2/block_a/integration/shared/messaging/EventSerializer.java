package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.messaging;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.IntegrationEvent;

// Serializer for integration events.
// The implementation may use JSON, Avro, Protobuf, or another format.
public interface EventSerializer {

    String serialize(IntegrationEvent event);

    <T extends IntegrationEvent> T deserialize(String payload, Class<T> eventClass);
}