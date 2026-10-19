package pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.application;

public record ProductChangedMessage(String eventId, String productId, long version, String name) {
}
