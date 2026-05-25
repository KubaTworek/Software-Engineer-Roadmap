package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.remote;

/**
 * Raised when a call is rejected because the circuit breaker is open.
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String dependencyName) {
        super("Circuit breaker is open for dependency: " + dependencyName);
    }
}