package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.legacy.dto;

import java.util.List;

// Legacy DTO coming from an external or old system.
// This model must not leak into the domain layer.
public record LegacyOrderDto(
        String legacyOrderId,
        String legacyCustomerId,
        List<LegacyOrderLineDto> lines
) {
}