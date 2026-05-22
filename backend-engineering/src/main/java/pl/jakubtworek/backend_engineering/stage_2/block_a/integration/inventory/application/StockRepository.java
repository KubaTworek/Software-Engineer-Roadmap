package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.inventory.application;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.OrderItemPayload;

import java.util.List;

// Port for inventory persistence.
// It should use local Inventory database, not the Sales database.
public interface StockRepository {

    boolean tryReserve(String orderId, List<OrderItemPayload> items);

    void releaseReservation(String orderId);
}