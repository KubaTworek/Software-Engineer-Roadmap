package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command;

import java.math.BigDecimal;
import java.util.List;

// Command representing the user's intention to place an order.
// It is independent from HTTP, JSON, Spring, or any framework.
public record PlaceOrderCommand(
        String customerId,
        String currency,
        List<PlaceOrderLineCommand> lines,
        BigDecimal expectedTotal
) {
}