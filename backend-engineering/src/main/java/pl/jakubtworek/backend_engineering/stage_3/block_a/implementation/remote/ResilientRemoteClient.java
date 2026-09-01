package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.remote;

import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreaker;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.Fallback;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.RetryExecutor;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutConfig;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutExecutor;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Production-path example that composes the canonical mechanisms from {@code concepts}.
 *
 * <p>The nesting is deliberate: {@code fallback(circuitBreaker(retry(timeout(call))))}.</p>
 *
 * <ol>
 *   <li>The timeout bounds every physical dependency attempt.</li>
 *   <li>Retry may repeat only failures accepted by its classifier.</li>
 *   <li>The circuit breaker observes one logical request after retries are exhausted.</li>
 *   <li>Fallback handles the final failure, including a fail-fast open circuit.</li>
 * </ol>
 *
 * <p>Putting retry outside the circuit breaker is usually dangerous: retries can consume
 * attempts on {@code CircuitBreakerOpenException} without reaching the dependency. It may
 * be intentional only with an explicit classifier and metrics proving the behavior.</p>
 */
public class ResilientRemoteClient {

    private final CircuitBreaker circuitBreaker;
    private final RetryExecutor retryExecutor;
    private final TimeoutExecutor timeoutExecutor;
    private final TimeoutConfig timeoutConfig;

    public ResilientRemoteClient(
            CircuitBreaker circuitBreaker,
            RetryExecutor retryExecutor,
            TimeoutExecutor timeoutExecutor,
            TimeoutConfig timeoutConfig
    ) {
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
        this.retryExecutor = Objects.requireNonNull(retryExecutor, "retryExecutor must not be null");
        this.timeoutExecutor = Objects.requireNonNull(timeoutExecutor, "timeoutExecutor must not be null");
        this.timeoutConfig = Objects.requireNonNull(timeoutConfig, "timeoutConfig must not be null");
    }

    public <T> T execute(Callable<T> remoteCall) throws Exception {
        Objects.requireNonNull(remoteCall, "remoteCall must not be null");
        return circuitBreaker.execute(() ->
                retryExecutor.execute(() ->
                        timeoutExecutor.execute(remoteCall, timeoutConfig.requestTimeout())
                )
        );
    }

    public <T> T execute(Callable<T> remoteCall, Fallback<T> fallback) throws Exception {
        Objects.requireNonNull(fallback, "fallback must not be null");
        try {
            return execute(remoteCall);
        } catch (Exception finalFailure) {
            return fallback.recover(finalFailure);
        }
    }
}
