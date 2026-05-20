package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration;

// Contract consumer used by downstream contexts.
// Each context decides how an incoming event affects its own model.
public interface IntegrationEventHandler<T extends IntegrationEvent> {

    void handle(T event);
}