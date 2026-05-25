package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.remote;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * Executes remote calls with an explicit request timeout.
 *
 * This implementation uses an ExecutorService for demonstration.
 * In real HTTP clients, prefer native timeout configuration as well.
 */
public class TimeoutExecutor implements AutoCloseable {

    private final ExecutorService executorService;

    public TimeoutExecutor(ExecutorService executorService) {
        if (executorService == null) {
            throw new IllegalArgumentException("executorService is required");
        }

        this.executorService = executorService;
    }

    public <T> T execute(Callable<T> operation, Duration timeout) throws Exception {
        Future<T> future = executorService.submit(operation);

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new RemoteCallTimeoutException("Remote call timed out after " + timeout);
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
        executorService.shutdownNow();
    }
}