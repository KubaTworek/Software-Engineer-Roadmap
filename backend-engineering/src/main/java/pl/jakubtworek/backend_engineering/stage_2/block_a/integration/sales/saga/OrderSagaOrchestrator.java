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
        OrderSaga saga = new OrderSaga(
                "SAGA-" + UUID.randomUUID(),
                event.orderId()
        );

        sagaRepository.save(saga);

        commandBus.sendAuthorizePayment(event.orderId());
    }

    public void on(PaymentCompletedEvent event) {
        OrderSaga saga = load(event.orderId());

        saga.markPaymentCompleted();
        sagaRepository.save(saga);

        commandBus.sendReserveInventory(event.orderId());
    }

    public void on(PaymentFailedEvent event) {
        OrderSaga saga = load(event.orderId());

        saga.markPaymentFailed();
        saga.fail();

        sagaRepository.save(saga);
    }

    public void on(InventoryReservedEvent event) {
        OrderSaga saga = load(event.orderId());

        saga.markInventoryReserved();

        if (saga.state() == OrderSagaState.READY_FOR_SHIPMENT) {
            commandBus.sendScheduleShipment(event.orderId());
            saga.complete();
        }

        sagaRepository.save(saga);
    }

    public void on(InventoryReservationFailedEvent event) {
        OrderSaga saga = load(event.orderId());

        saga.markInventoryFailed();
        saga.startCompensation();

        commandBus.sendCancelPayment(event.orderId());

        saga.fail();
        sagaRepository.save(saga);
    }

    private OrderSaga load(String orderId) {
        return sagaRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Saga not found for order: " + orderId));
    }
}