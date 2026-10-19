package pl.jakubtworek.backend_engineering.stage_2.block_a.test.integration;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.OrderPlacedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.CustomerId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Money;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event.OrderPlacedIntegrationEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event.SalesIntegrationEventMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class DomainToIntegrationEventMappingTest {

    @Test
    void mapsInternalValueObjectsToAVersionedPublishedContract() {
        OrderPlacedEvent domainEvent = new OrderPlacedEvent(
                "event-17",
                Instant.parse("2026-08-31T10:15:30Z"),
                OrderId.of("O-17"),
                CustomerId.of("C-5"),
                Money.of(new BigDecimal("149.90"), Currency.getInstance("PLN"))
        );

        OrderPlacedIntegrationEvent integrationEvent = (OrderPlacedIntegrationEvent)
                new SalesIntegrationEventMapper().map(domainEvent);

        assertThat(integrationEvent.messageId()).isEqualTo("event-17");
        assertThat(integrationEvent.aggregateId()).isEqualTo("O-17");
        assertThat(integrationEvent.customerId()).isEqualTo("C-5");
        assertThat(integrationEvent.total()).isEqualByComparingTo("149.90");
        assertThat(integrationEvent.currency()).isEqualTo("PLN");
        assertThat(integrationEvent.schemaVersion()).isEqualTo(1);
        assertThat(integrationEvent.eventType()).isEqualTo("sales.order-placed.v1");
    }
}
