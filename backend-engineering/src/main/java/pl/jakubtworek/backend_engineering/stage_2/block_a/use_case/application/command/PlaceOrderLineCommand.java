package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command;

import java.math.BigDecimal;

// Command item received by the application service.
// It contains input data but no business behavior.
public record PlaceOrderLineCommand(
        String productId,
        int quantity,
        BigDecimal unitPrice
) {
}