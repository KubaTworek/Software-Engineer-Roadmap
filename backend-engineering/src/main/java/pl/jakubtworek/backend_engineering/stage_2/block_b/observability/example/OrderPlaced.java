package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.example;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.EventMetadata;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.ObservableEvent;

import java.math.BigDecimal;

/**
 * Example event used to demonstrate observability.
 */
public record OrderPlaced(
        EventMetadata metadata,
        String orderId,
        BigDecimal totalAmount
) implements ObservableEvent {

    public static final String TYPE = "OrderPlaced";

    /**
     * Returns orderId as the aggregate identifier.
     */
    @Override
    public String aggregateId() {
        return orderId;
    }

    /**
     * Returns the logical event type.
     */
    @Override
    public String eventType() {
        return TYPE;
    }
}