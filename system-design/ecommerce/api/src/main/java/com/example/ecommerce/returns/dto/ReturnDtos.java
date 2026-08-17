package com.example.ecommerce.returns.dto;

import com.example.ecommerce.returns.ReturnStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReturnDtos {
    private ReturnDtos() {}

    public record ReturnItemRequest(
            @NotNull Long orderItemId,
            @NotNull @Min(1) Integer quantity,
            @NotNull BigDecimal refundAmount
    ) {}

    public record CreateReturnRequest(
            @NotNull Long orderId,
            @NotBlank String reason,
            List<ReturnItemRequest> items
    ) {}

    public record ReturnResponse(
            Long id,
            Long orderId,
            ReturnStatus status,
            BigDecimal requestedRefundAmount,
            String reason,
            Instant createdAt
    ) {}
}
