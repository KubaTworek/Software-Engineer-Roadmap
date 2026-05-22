package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.contracts;

// Minimal data transferred between contexts.
// This is not a domain entity, only a contract payload.
public record OrderItemPayload(
        String productId,
        int quantity
) {
}