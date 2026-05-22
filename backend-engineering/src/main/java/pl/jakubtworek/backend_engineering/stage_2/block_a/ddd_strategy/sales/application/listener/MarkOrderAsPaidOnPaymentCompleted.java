package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.application.listener;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.billing.api.events.PaymentCompletedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.application.service.SalesOrderApplicationService;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration.IntegrationEventHandler;

// Sales reacts to PaymentCompleted from Billing.
// Sales does not own the payment model, but may update its order status.
public final class MarkOrderAsPaidOnPaymentCompleted
        implements IntegrationEventHandler<PaymentCompletedEvent> {

    private final SalesOrderApplicationService orderService;

    public MarkOrderAsPaidOnPaymentCompleted(SalesOrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void handle(PaymentCompletedEvent event) {
        orderService.markOrderAsPaid(
                event.orderId(),
                event.paymentId()
        );
    }
}