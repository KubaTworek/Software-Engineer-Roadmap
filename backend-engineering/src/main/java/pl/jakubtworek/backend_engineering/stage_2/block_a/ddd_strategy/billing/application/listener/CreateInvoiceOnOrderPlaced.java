package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.billing.application.listener;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.billing.application.service.InvoiceApplicationService;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.api.events.OrderPlacedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration.IntegrationEventHandler;

// Billing reacts to OrderPlaced by creating an invoice.
// Billing owns its own model and does not reuse Sales entities.
public final class CreateInvoiceOnOrderPlaced
        implements IntegrationEventHandler<OrderPlacedEvent> {

    private final InvoiceApplicationService invoiceService;

    public CreateInvoiceOnOrderPlaced(InvoiceApplicationService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Override
    public void handle(OrderPlacedEvent event) {
        invoiceService.createInvoice(
                event.orderId(),
                event.customerId(),
                event.total()
        );
    }
}