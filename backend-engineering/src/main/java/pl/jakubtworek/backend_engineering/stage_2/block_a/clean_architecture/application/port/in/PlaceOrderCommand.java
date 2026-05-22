package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in;

// Command object used by the input port.
// It is not an HTTP request and does not depend on any web framework.
public record PlaceOrderCommand(
        String customerId
) {
}