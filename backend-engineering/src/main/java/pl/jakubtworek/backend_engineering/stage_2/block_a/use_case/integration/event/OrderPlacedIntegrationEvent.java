package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Published-language representation containing primitives instead of domain value objects. */
public record OrderPlacedIntegrationEvent(
        String messageId,
        Instant occurredAt,
        String orderId,
        String customerId,
        BigDecimal total,
        String currency,
        int schemaVersion
) implements IntegrationEvent {

    @Override
    public String aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return "sales.order-placed.v" + schemaVersion;
    }
}
