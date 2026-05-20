package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.DomainEvent;

// Serializer for domain events stored in the outbox.
// The implementation may use Jackson, Avro, Protobuf, or another format.
public interface DomainEventSerializer {

    String serialize(DomainEvent event);
}