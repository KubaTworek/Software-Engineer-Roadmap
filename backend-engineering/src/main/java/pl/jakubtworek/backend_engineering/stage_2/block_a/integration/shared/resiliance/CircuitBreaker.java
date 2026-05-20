package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.resiliance;

// Simple circuit breaker abstraction.
// It prevents repeatedly calling an unhealthy dependency.
public interface CircuitBreaker {

    void execute(Runnable action);
}