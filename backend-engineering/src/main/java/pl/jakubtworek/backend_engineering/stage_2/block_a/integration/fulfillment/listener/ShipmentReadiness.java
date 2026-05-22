package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.fulfillment.listener;

// Local Fulfillment process state.
// It tracks whether all required events have arrived before shipment can be scheduled.
public final class ShipmentReadiness {

    private final String orderId;
    private boolean paymentCompleted;
    private boolean inventoryReserved;
    private boolean shipmentScheduled;

    private ShipmentReadiness(String orderId) {
        this.orderId = orderId;
    }

    public static ShipmentReadiness forOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("OrderId cannot be empty");
        }

        return new ShipmentReadiness(orderId);
    }

    public void markPaymentCompleted() {
        this.paymentCompleted = true;
    }

    public void markInventoryReserved() {
        this.inventoryReserved = true;
    }

    public void markShipmentScheduled() {
        this.shipmentScheduled = true;
    }

    public boolean readyForShipment() {
        return paymentCompleted && inventoryReserved && !shipmentScheduled;
    }

    public String orderId() {
        return orderId;
    }

    public boolean paymentCompleted() {
        return paymentCompleted;
    }

    public boolean inventoryReserved() {
        return inventoryReserved;
    }

    public boolean shipmentScheduled() {
        return shipmentScheduled;
    }
}