package pl.jakubtworek.durable;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface PaymentActivities {
    void charge(String idempotencyKey);
    void release(String idempotencyKey);
    void fulfill(String orderId);
}
