package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.resilience;

import java.util.concurrent.Callable;

/**
 * Combines timeout, retry, and circuit breaker around one remote dependency.
 *
 * Execution order:
 * 1. Circuit breaker decides whether dependency may be called.
 * 2. Retry executor retries transient failures.
 * 3. Timeout executor bounds every attempt.
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
        this.circuitBreaker = circuitBreaker;
        this.retryExecutor = retryExecutor;
        this.timeoutExecutor = timeoutExecutor;
        this.timeoutConfig = timeoutConfig;
    }

    public <T> T execute(Callable<T> remoteCall) throws Exception {
        return circuitBreaker.execute(() ->
                retryExecutor.execute(() ->
                        timeoutExecutor.execute(remoteCall, timeoutConfig.requestTimeout())
                )
        );
    }
}