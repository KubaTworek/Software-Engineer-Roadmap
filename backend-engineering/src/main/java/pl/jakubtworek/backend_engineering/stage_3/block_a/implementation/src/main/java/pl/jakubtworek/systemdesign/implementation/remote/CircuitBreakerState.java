package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.remote;

/**
 * Circuit breaker state.
 */
public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}