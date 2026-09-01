package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

/** Request shape owned by the HTTP adapter, not by the application or domain. */
public record PlaceOrderHttpRequest(
        String customerId,
        String currency,
        List<PlaceOrderLineHttpRequest> lines,
        BigDecimal expectedTotal
) {

    public PlaceOrderHttpRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
