package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.application.command;

import java.util.List;

// Application command used inside the Sales context.
// It represents the intention to place an order.
public record PlaceOrderCommand(
        String customerId,
        List<PlaceOrderItem> items
) {
}