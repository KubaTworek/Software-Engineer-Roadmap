package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.antipattern;

// Database row shared between multiple contexts.
// This should be avoided because it becomes a hidden integration contract.
public record SharedOrderRow(
        String orderId,
        String customerId,
        String paymentStatus,
        String shipmentStatus,
        String totalAmount
) {
}