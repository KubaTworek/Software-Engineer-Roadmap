package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.shipping;

import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.EventMetadata;

/**
 * Event emitted when the shipping process has been initiated.
 *
 * This does not necessarily mean that the package has already left the warehouse.
 * It only means that the shipping service accepted the order for fulfillment.
 */
public record ShippingInitiated(
        EventMetadata metadata,
        String orderId,
        String shipmentId
) implements DomainEvent {

    public static final String TYPE = "ShippingInitiated";
    public static final int VERSION = 1;

    @Override
    public String aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}