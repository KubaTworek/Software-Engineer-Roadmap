package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.sales.saga;

import java.time.Instant;

// Saga instance representing one long-running business process.
// It should be stored durably because the process may span multiple messages and retries.
public final class OrderSaga {

    private final String sagaId;
    private final String orderId;
    private OrderSagaState state;
    private Instant updatedAt;

    public OrderSaga(String sagaId, String orderId) {
        this.sagaId = sagaId;
        this.orderId = orderId;
        this.state = OrderSagaState.STARTED;
        this.updatedAt = Instant.now();
    }

    public void markPaymentCompleted() {
        if (state == OrderSagaState.STARTED) {
            state = OrderSagaState.PAYMENT_COMPLETED;
            updatedAt = Instant.now();
        }
    }

    public void markPaymentFailed() {
        state = OrderSagaState.PAYMENT_FAILED;
        updatedAt = Instant.now();
    }

    public void markInventoryReserved() {
        if (state == OrderSagaState.PAYMENT_COMPLETED) {
            state = OrderSagaState.READY_FOR_SHIPMENT;
        } else {
            state = OrderSagaState.INVENTORY_RESERVED;
        }

        updatedAt = Instant.now();
    }

    public void markInventoryFailed() {
        state = OrderSagaState.INVENTORY_FAILED;
        updatedAt = Instant.now();
    }

    public void startCompensation() {
        state = OrderSagaState.COMPENSATING;
        updatedAt = Instant.now();
    }

    public void complete() {
        state = OrderSagaState.COMPLETED;
        updatedAt = Instant.now();
    }

    public void fail() {
        state = OrderSagaState.FAILED;
        updatedAt = Instant.now();
    }

    public String sagaId() {
        return sagaId;
    }

    public String orderId() {
        return orderId;
    }

    public OrderSagaState state() {
        return state;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}