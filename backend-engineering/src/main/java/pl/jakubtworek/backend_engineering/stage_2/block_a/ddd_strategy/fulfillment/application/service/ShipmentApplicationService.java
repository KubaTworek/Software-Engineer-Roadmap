package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.fulfillment.application.service;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.contracts.OrderItemPayload;

import java.util.List;

// Application service boundary for Fulfillment.
// The implementation belongs to the Fulfillment context.
public interface ShipmentApplicationService {

    void scheduleShipment(
            String orderId,
            String customerId,
            List<OrderItemPayload> items
    );
}