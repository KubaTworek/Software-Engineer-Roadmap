package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.application.command;

// Command item used by the application service.
public record PlaceOrderLineCommand(
        String productId,
        int quantity
) {
}