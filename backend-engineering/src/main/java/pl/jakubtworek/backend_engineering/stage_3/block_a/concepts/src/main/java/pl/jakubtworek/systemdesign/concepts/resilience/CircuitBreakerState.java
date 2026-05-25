package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.resilience;

/**
 * Circuit breaker state.
 */
public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}