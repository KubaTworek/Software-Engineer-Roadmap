package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.fulfillment.application.listener;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.fulfillment.application.service.ShipmentApplicationService;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.api.events.OrderPlacedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration.IntegrationEventHandler;

// Fulfillment reacts to OrderPlaced without depending on Sales internals.
// It consumes only the published integration contract.
public final class ScheduleShipmentOnOrderPlaced
        implements IntegrationEventHandler<OrderPlacedEvent> {

    private final ShipmentApplicationService shipmentService;

    public ScheduleShipmentOnOrderPlaced(ShipmentApplicationService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @Override
    public void handle(OrderPlacedEvent event) {
        shipmentService.scheduleShipment(
                event.orderId(),
                event.customerId(),
                event.items()
        );
    }
}