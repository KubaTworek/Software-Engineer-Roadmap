package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.in.web;

// HTTP response DTO.
// It is a transport object, not a domain object.
public record PlaceOrderHttpResponse(
        String orderId,
        String status
) {
}