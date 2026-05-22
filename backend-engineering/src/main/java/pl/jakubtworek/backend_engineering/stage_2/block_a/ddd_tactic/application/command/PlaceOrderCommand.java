package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.application.command;

import java.util.List;

// Application command representing a use case request.
// It should be simple and should not contain domain behavior.
public record PlaceOrderCommand(
        String customerId,
        String currency,
        List<PlaceOrderLineCommand> lines
) {
}