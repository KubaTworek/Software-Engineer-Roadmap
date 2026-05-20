package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.application.command;

// Command item used by the Sales application layer.
public record PlaceOrderItem(
        String productId,
        int quantity
) {
}