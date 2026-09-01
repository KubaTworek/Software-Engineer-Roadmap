package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreaker;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.RetryClassifier;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutExecutor;

import java.time.Duration;
import java.util.Objects;

/**
 * One executable path combining deadline propagation, retry budget, circuit
 * breaker, per-dependency bulkhead and a timeout for every physical attempt.
 */
public final class OverloadProtectedRemoteClient {

    private final CircuitBreaker circuitBreaker;
    private final SemaphoreBulkhead bulkhead;
    private final RetryBudget retryBudget;
    private final RetryClassifier retryClassifier;
    private final TimeoutExecutor timeoutExecutor;

    public OverloadProtectedRemoteClient(
            CircuitBreaker circuitBreaker,
            SemaphoreBulkhead bulkhead,
            RetryBudget retryBudget,
            RetryClassifier retryClassifier,
            TimeoutExecutor timeoutExecutor
    ) {
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.bulkhead = Objects.requireNonNull(bulkhead, "bulkhead");
        this.retryBudget = Objects.requireNonNull(retryBudget, "retryBudget");
        this.retryClassifier = Objects.requireNonNull(retryClassifier, "retryClassifier");
        this.timeoutExecutor = Objects.requireNonNull(timeoutExecutor, "timeoutExecutor");
    }

    public <T> T execute(
            RequestDeadline requestDeadline,
            RetryPolicy retryPolicy,
            DependencyCall<T> dependencyCall
    ) throws Exception {
        Objects.requireNonNull(requestDeadline, "requestDeadline");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        Objects.requireNonNull(dependencyCall, "dependencyCall");

        requestDeadline.throwIfExpired();
        return circuitBreaker.execute(() -> executeAttempts(requestDeadline, retryPolicy, dependencyCall));
    }

    private <T> T executeAttempts(
            RequestDeadline requestDeadline,
            RetryPolicy retryPolicy,
            DependencyCall<T> dependencyCall
    ) throws Exception {
        int attempt = 1;
        while (true) {
            RequestDeadline downstreamDeadline = requestDeadline.child(
                    retryPolicy.maximumAttemptTime(), retryPolicy.parentReserve());
            Duration attemptTimeout = downstreamDeadline.remaining();

            try (SemaphoreBulkhead.Permit ignored = bulkhead.enter()) {
                int currentAttempt = attempt;
                return timeoutExecutor.execute(
                        () -> dependencyCall.invoke(new AttemptContext(
                                currentAttempt,
                                downstreamDeadline.toHeaderValue(),
                                attemptTimeout
                        )),
                        attemptTimeout
                );
            } catch (Exception failure) {
                boolean attemptsRemain = attempt < retryPolicy.maxAttempts();
                if (!attemptsRemain || !retryClassifier.isRetryable(failure)) {
                    throw failure;
                }
                requestDeadline.throwIfExpired();
                if (!retryBudget.tryConsumeRetry()) {
                    throw failure;
                }
                attempt++;
            }
        }
    }

    @FunctionalInterface
    public interface DependencyCall<T> {
        T invoke(AttemptContext context) throws Exception;
    }

    public record AttemptContext(int attempt, String deadlineHeader, Duration allocatedTime) {
    }
}
