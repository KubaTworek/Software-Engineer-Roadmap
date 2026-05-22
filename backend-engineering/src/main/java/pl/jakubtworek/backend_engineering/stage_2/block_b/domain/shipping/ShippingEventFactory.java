package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.shipping;

import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.EventMetadata;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.payment.PaymentAuthorized;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.shipping.ShippingInitiated;

/**
 * Factory responsible for creating shipping-related integration events.
 */
public class ShippingEventFactory {

    private static final String SOURCE_SERVICE = "shipping-service";

    /**
     * Creates a ShippingInitiated event caused by PaymentAuthorized.
     */
    public ShippingInitiated shippingInitiated(
            Shipment shipment,
            PaymentAuthorized causedBy
    ) {
        EventMetadata metadata = EventMetadata.causedBy(
                causedBy.metadata(),
                SOURCE_SERVICE,
                ShippingInitiated.VERSION
        );

        return new ShippingInitiated(
                metadata,
                shipment.orderId(),
                shipment.shipmentId()
        );
    }
}