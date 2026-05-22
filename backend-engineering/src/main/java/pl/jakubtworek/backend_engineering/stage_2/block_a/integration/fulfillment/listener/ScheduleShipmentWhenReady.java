package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.fulfillment.listener;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.fulfillment.application.ShipmentService;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.InventoryReservedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.PaymentCompletedEvent;

// Choreography-based process manager.
// It waits until both payment and inventory reservation are completed before scheduling shipment.
public final class ScheduleShipmentWhenReady {

    private final ShipmentService shipmentService;
    private final ShipmentReadinessRepository readinessRepository;

    public ScheduleShipmentWhenReady(
            ShipmentService shipmentService,
            ShipmentReadinessRepository readinessRepository
    ) {
        this.shipmentService = shipmentService;
        this.readinessRepository = readinessRepository;
    }

    public void handle(PaymentCompletedEvent event) {
        ShipmentReadiness readiness = readinessRepository.findByOrderId(event.orderId())
                .orElseGet(() -> ShipmentReadiness.forOrder(event.orderId()));

        readiness.markPaymentCompleted();

        if (readiness.readyForShipment()) {
            shipmentService.scheduleShipment(event.orderId());
            readiness.markShipmentScheduled();
        }

        readinessRepository.save(readiness);
    }

    public void handle(InventoryReservedEvent event) {
        ShipmentReadiness readiness = readinessRepository.findByOrderId(event.orderId())
                .orElseGet(() -> ShipmentReadiness.forOrder(event.orderId()));

        readiness.markInventoryReserved();

        if (readiness.readyForShipment()) {
            shipmentService.scheduleShipment(event.orderId());
            readiness.markShipmentScheduled();
        }

        readinessRepository.save(readiness);
    }
}