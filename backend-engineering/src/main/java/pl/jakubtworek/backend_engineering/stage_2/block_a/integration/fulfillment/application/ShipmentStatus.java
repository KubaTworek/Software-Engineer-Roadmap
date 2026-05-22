package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.fulfillment.application;

// Shipment lifecycle state inside the Fulfillment context.
public enum ShipmentStatus {
    SCHEDULED,
    PICKING,
    DISPATCHED,
    DELIVERED,
    CANCELLED
}