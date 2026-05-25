package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.dto;

/**
 * Response returned after an order is accepted.
 *
 * The response can be safely cached by idempotency logic and returned again
 * when the same Idempotency-Key is used by the client.
 */
public record OrderCreatedResponse(Long orderId, String status) {}
