package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * Executes a call with request timeout.
 *
 * Native HTTP client timeouts should also be configured.
 * This wrapper is useful when the operation itself does not expose timeout handling.
 */
public class TimeoutExecutor implements AutoCloseable {

    private final ExecutorService executor;

    public TimeoutExecutor(ExecutorService executor) {
        if (executor == null) throw new IllegalArgumentException("executor is required");
        this.executor = executor;
    }

    public <T> T execute(Callable<T> operation, Duration timeout) throws Exception {
        if (operation == null) throw new IllegalArgumentException("operation is required");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        Future<T> future = executor.submit(operation);

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new RemoteTimeoutException("Remote call timed out after " + timeout);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof Exception e) {
                throw e;
            }

            throw new RuntimeException(cause);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
