package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.billing.application;

import java.math.BigDecimal;

// Port for an external payment provider.
// Billing depends on this abstraction, not on a concrete provider SDK.
public interface PaymentGateway {

    PaymentResult authorize(
            String orderId,
            String customerId,
            BigDecimal amount,
            String currency
    );
}