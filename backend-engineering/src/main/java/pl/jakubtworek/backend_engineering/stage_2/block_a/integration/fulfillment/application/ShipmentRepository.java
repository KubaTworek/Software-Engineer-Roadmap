package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.fulfillment.application;

// Repository owned by Fulfillment.
// It stores shipments in the Fulfillment database, not in the Sales database.
public interface ShipmentRepository {

    boolean existsForOrder(String orderId);

    void save(Shipment shipment);
}