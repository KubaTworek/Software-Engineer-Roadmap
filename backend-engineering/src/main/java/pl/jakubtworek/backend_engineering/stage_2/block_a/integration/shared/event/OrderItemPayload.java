package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event;

// Payload used by integration events.
// It is a contract object, not a domain entity.
public record OrderItemPayload(
        String productId,
        int quantity
) {
}