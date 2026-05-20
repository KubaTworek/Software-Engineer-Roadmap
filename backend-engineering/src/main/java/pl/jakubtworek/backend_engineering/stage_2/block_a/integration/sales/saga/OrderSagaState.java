package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.sales.saga;

// State of a long-running order saga.
// It tracks progress across Billing, Inventory, and Fulfillment.
public enum OrderSagaState {
    STARTED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    INVENTORY_RESERVED,
    INVENTORY_FAILED,
    READY_FOR_SHIPMENT,
    COMPENSATING,
    COMPLETED,
    FAILED
}