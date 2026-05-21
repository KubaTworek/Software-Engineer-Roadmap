package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.shipping;

/**
 * Domain object representing a shipment.
 *
 * This class belongs to the Shipping Service boundary.
 */
public class Shipment {

    private final String shipmentId;
    private final String orderId;
    private ShipmentStatus status;

    public Shipment(
            String shipmentId,
            String orderId
    ) {
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.status = ShipmentStatus.INITIATED;
    }

    /**
     * Marks the shipment as dispatched.
     */
    public void dispatch() {
        if (status != ShipmentStatus.INITIATED) {
            throw new IllegalStateException("Only initiated shipments can be dispatched.");
        }

        this.status = ShipmentStatus.DISPATCHED;
    }

    public String shipmentId() {
        return shipmentId;
    }

    public String orderId() {
        return orderId;
    }

    public ShipmentStatus status() {
        return status;
    }
}