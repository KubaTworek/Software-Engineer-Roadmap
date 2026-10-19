package pl.jakubtworek.cloudarchitecture.dto;

/**
 * Response returned after an order is accepted.
 *
 * The response can be safely cached by idempotency logic and returned again
 * when the same Idempotency-Key is used by the client.
 */
public record OrderCreatedResponse(Long orderId, String status) {
    public OrderCreatedResponse {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
    }
}
