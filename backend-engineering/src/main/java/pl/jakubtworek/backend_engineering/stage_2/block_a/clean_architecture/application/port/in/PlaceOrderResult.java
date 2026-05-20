package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in;

// Result object returned by the use case.
// It is not a ResponseEntity and does not depend on HTTP.
public record PlaceOrderResult(
        String orderId,
        String status
) {
}