package pl.jakubtworek.marketplace.payment.infrastructure.temporal;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface PaymentLookupActivity {
    String reconcile(String orderId, String correlationId);
}
