package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.fulfillment.application;

// Application service responsible for scheduling shipment.
// It belongs to the Fulfillment context and uses only Fulfillment-owned data.
public final class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public void scheduleShipment(String orderId) {
        if (shipmentRepository.existsForOrder(orderId)) {
            return;
        }

        Shipment shipment = Shipment.scheduleForOrder(orderId);

        shipmentRepository.save(shipment);
    }
}