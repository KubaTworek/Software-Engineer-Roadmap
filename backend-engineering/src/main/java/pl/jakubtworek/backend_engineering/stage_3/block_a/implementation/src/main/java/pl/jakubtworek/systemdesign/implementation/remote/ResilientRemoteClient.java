package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.remote;

import java.util.concurrent.Callable;

/**
 * Combines timeout, retry, and circuit breaker around a remote dependency.
 *
 * Execution order:
 * 1. Circuit breaker decides whether the call is isAllowed.
 * 2. Retry executor handles transient failures.
 * 3. Timeout executor bounds each individual attempt.
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