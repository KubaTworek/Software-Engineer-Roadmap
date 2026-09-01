package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.sales.saga;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.*;

import java.util.UUID;

// Orchestration-based saga coordinator.
// It explicitly controls the order process across multiple bounded contexts.
public final class OrderSagaOrchestrator {

    private final OrderSagaRepository sagaRepository;
    private final SagaCommandBus commandBus;

    public OrderSagaOrchestrator(
            OrderSagaRepository sagaRepository,
            SagaCommandBus commandBus
    ) {
        this.sagaRepository = sagaRepository;
        this.commandBus = commandBus;
    }

    public void on(OrderPlacedEvent event) {
        if (sagaRepository.findByOrderId(event.orderId()).isPresent()) {
            return;
        }

        OrderSaga saga = new OrderSaga(
                "SAGA-" + UUID.randomUUID(),
                event.orderId()
        );

        sagaRepository.save(saga);

        // Persisting saga state and dispatching the command are two writes.
        // A durable implementation stores the command in an outbox in the same
        // transaction as the saga state. The command handler must be idempotent.
        commandBus.sendAuthorizePayment(event.orderId());
    }

    public void on(PaymentCompletedEvent event) {
        OrderSaga saga = load(event.orderId());
        if (saga.state() != OrderSagaState.STARTED) {
            return;
        }

        saga.markPaymentCompleted();
        sagaRepository.save(saga);

        commandBus.sendReserveInventory(event.orderId());
    }

    public void on(PaymentFailedEvent event) {
        OrderSaga saga = load(event.orderId());
        if (saga.state() != OrderSagaState.STARTED) {
            return;
        }

        saga.markPaymentFailed();
        saga.fail();

        sagaRepository.save(saga);
    }

    public void on(InventoryReservedEvent event) {
        OrderSaga saga = load(event.orderId());
        if (saga.state() != OrderSagaState.PAYMENT_COMPLETED) {
            return;
        }

        saga.markInventoryReserved();

        if (saga.state() == OrderSagaState.READY_FOR_SHIPMENT) {
            // This command has the same dual-write problem. Treat the direct
            // call as a teaching shortcut, not an exactly-once guarantee.
            commandBus.sendScheduleShipment(event.orderId());
            saga.complete();
        }

        sagaRepository.save(saga);
    }

    public void on(InventoryReservationFailedEvent event) {
        OrderSaga saga = load(event.orderId());
        if (saga.state() != OrderSagaState.PAYMENT_COMPLETED) {
            return;
        }

        saga.markInventoryFailed();
        saga.startCompensation();

        // Compensation is a new business operation, not a rollback. It can
        // fail independently and therefore also needs durable, retryable dispatch.
        commandBus.sendCancelPayment(event.orderId());

        saga.fail();
        sagaRepository.save(saga);
    }

    private OrderSaga load(String orderId) {
        return sagaRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Saga not found for order: " + orderId));
    }
}
