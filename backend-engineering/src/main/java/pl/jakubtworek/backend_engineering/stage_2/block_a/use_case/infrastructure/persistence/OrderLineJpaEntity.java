package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence;

// Persistence representation of an order line. It deliberately contains no domain behavior.
public record OrderLineJpaEntity(
        String productId,
        int quantity,
        String unitPrice
) {
}
