package pl.jakubtworek.booking.service.async;

import java.time.Duration;
import java.util.concurrent.*;

final class CompletableFutureTimeouts {
    private CompletableFutureTimeouts() {
    }

    static <T> CompletableFuture<T> withTimeout(
            CompletableFuture<T> original,
            Duration timeout,
            ScheduledExecutorService scheduler
    ) {
        CompletableFuture<T> result = new CompletableFuture<>();

        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            TimeoutException timeoutException = new TimeoutException("Operation timed out after " + timeout);
            if (result.completeExceptionally(timeoutException)) {
                original.cancel(true);
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        original.whenComplete((value, error) -> {
            timeoutTask.cancel(false);
            if (error == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(error);
            }
        });

        return result;
    }
}
