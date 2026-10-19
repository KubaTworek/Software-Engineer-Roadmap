package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event.IntegrationEvent;

/** Serialization belongs outside the domain-to-contract mapping boundary. */
@FunctionalInterface
public interface IntegrationEventSerializer {

    String serialize(IntegrationEvent event);
}
