package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.orders;

import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.EventMetadata;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.order.OrderPlaced;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.order.OrderPlacedItem;

import java.util.List;

/**
 * Factory responsible for translating internal Order domain objects
 * into public integration events.
 *
 * Keeping this mapping in one place reduces accidental coupling between
 * the internal domain model and external event contracts.
 */
public class OrderEventFactory {

    private static final String SOURCE_SERVICE = "order-service";

    /**
     * Creates an OrderPlaced event from an Order aggregate.
     *
     * The correlationId is set to orderId in this simple example.
     */
    public OrderPlaced orderPlaced(Order order) {
        EventMetadata metadata = EventMetadata.rootEvent(
                order.orderId(),
                SOURCE_SERVICE,
                OrderPlaced.VERSION
        );

        List<OrderPlacedItem> eventItems = order.items()
                .stream()
                .map(item -> new OrderPlacedItem(
                        item.productId(),
                        item.quantity(),
                        item.unitPrice()
                ))
                .toList();

        return new OrderPlaced(
                metadata,
                order.orderId(),
                eventItems,
                order.totalAmount()
        );
    }
}