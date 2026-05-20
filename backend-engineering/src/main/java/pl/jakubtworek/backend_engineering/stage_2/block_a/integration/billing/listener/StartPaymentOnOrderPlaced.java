package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.billing.listener;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.billing.application.PaymentService;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.OrderPlacedEvent;

// Choreography handler.
// Billing independently reacts to OrderPlaced without being directly called by Sales.
public final class StartPaymentOnOrderPlaced {

    private final PaymentService paymentService;

    public StartPaymentOnOrderPlaced(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void handle(OrderPlacedEvent event, String correlationId) {
        paymentService.authorizePayment(event, correlationId);
    }
}