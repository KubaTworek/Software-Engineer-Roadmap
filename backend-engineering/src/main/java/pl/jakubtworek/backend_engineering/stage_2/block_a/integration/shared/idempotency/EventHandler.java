package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.idempotency;

// Functional interface for domain-specific event handlers.
public interface EventHandler<T> {

    void handle(T event);
}