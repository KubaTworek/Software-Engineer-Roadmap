package pl.jakubtworek.backend.payment.api;

import java.util.UUID;

public record PaymentResponse(UUID paymentId, UUID orderId, String status) {
}
