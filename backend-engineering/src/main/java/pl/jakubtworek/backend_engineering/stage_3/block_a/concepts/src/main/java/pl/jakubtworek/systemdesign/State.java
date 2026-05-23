package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

/**
 * Circuit breaker states.
 */
public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
}
