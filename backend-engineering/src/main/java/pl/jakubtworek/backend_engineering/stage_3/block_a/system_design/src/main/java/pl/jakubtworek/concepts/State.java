package pl.jakubtworek.backend_engineering.stage_3.block_a.system_design.src.main.java.pl.jakubtworek.concepts;

/**
 * Circuit breaker states.
 */
public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
}
