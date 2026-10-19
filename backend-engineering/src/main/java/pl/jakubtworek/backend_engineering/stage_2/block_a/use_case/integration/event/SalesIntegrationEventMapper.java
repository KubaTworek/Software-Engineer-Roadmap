package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.OrderPlacedEvent;

/** Explicit anti-corruption boundary between the internal domain event and public schema. */
public final class SalesIntegrationEventMapper implements DomainEventToIntegrationEventMapper {

    private static final int ORDER_PLACED_SCHEMA_VERSION = 1;

    @Override
    public IntegrationEvent map(DomainEvent event) {
        if (event instanceof OrderPlacedEvent orderPlaced) {
            return new OrderPlacedIntegrationEvent(
                    orderPlaced.eventId(),
                    orderPlaced.occurredAt(),
                    orderPlaced.orderId().value(),
                    orderPlaced.customerId().value(),
                    orderPlaced.total().amount(),
                    orderPlaced.total().currency().getCurrencyCode(),
                    ORDER_PLACED_SCHEMA_VERSION
            );
        }

        throw new IllegalArgumentException("Unsupported domain event: " + event.eventType());
    }
}
