package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PaymentProviderClient {
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean lastSlowCallInterrupted = new AtomicBoolean(false);

    public PaymentProviderClient(ThreadPoolExecutor bookingAsyncExecutor) {
        this.executor = bookingAsyncExecutor;
    }

    public CompletableFuture<PaymentValidationResult> validatePayment(UUID reservationId, PaymentScenario scenario) {
        CompletableFuture<PaymentValidationResult> promise = new CompletableFuture<>();

        Future<?> runningTask = executor.submit(() -> {
            try {
                PaymentValidationResult result = switch (scenario) {
                    case APPROVED -> PaymentValidationResult.approved();
                    case DECLINED -> PaymentValidationResult.declined("PAYMENT_DECLINED");
                    case FAILING -> throw new IllegalStateException("Payment provider technical failure");
                    case SLOW -> slowPaymentValidation();
                };
                promise.complete(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lastSlowCallInterrupted.set(true);
                promise.completeExceptionally(new CancellationException("Payment validation interrupted"));
            } catch (Throwable error) {
                promise.completeExceptionally(error);
            }
        });

        promise.whenComplete((result, error) -> {
            if (promise.isCancelled()) {
                runningTask.cancel(true);
            }
        });

        return promise;
    }

    private PaymentValidationResult slowPaymentValidation() throws InterruptedException {
        lastSlowCallInterrupted.set(false);
        Thread.sleep(5_000);
        return PaymentValidationResult.approved();
    }

    public boolean wasLastSlowCallInterrupted() {
        return lastSlowCallInterrupted.get();
    }
}
