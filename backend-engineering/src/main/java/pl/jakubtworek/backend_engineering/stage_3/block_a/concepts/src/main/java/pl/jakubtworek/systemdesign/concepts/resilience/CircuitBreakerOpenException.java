package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.resilience;

/**
 * Raised when a dependency call is rejected because circuit breaker is open.
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String dependencyName) {
        super("Circuit breaker is open for dependency: " + dependencyName);
    }
}