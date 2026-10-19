package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.adapter.in.web;

import java.math.BigDecimal;

/** Transport DTO. It deliberately contains no domain behavior. */
public record PlaceOrderLineHttpRequest(
        String productId,
        int quantity,
        BigDecimal unitPrice
) {
}
