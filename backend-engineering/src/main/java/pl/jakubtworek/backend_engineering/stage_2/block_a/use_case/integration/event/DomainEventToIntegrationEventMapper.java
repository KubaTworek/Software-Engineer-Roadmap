package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.DomainEvent;

@FunctionalInterface
public interface DomainEventToIntegrationEventMapper {

    IntegrationEvent map(DomainEvent event);
}
