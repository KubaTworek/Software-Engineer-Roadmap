package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration;

// Optional contract metadata used for versioning published language.
// Event contracts should evolve explicitly and backward compatibility should be considered.
public record EventContractDescriptor(
        String eventType,
        int version,
        String ownerContext,
        String description
) {
}