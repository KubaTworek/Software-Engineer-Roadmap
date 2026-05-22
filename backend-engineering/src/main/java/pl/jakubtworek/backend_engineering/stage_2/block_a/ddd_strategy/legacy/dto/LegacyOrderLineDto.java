package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.legacy.dto;

// Legacy line representation.
// It belongs to the external model, not to the Sales domain.
public record LegacyOrderLineDto(
        String sku,
        int qty
) {
}