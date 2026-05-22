package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.in.web;

// HTTP request DTO.
// It belongs to the web adapter and must not leak into the application or domain layer.
public record PlaceOrderHttpRequest(
        String customerId
) {
}