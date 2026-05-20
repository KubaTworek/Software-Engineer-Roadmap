package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.billing.application.service;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.contracts.MoneyPayload;

// Application service boundary for Billing.
// The implementation belongs to the Billing context.
public interface InvoiceApplicationService {

    void createInvoice(
            String orderId,
            String customerId,
            MoneyPayload total
    );
}