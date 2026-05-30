package pl.jakubtworek.booking.dto.security;

import java.util.UUID;

public record PaymentSummaryResponse(UUID reservationId, String paymentStatus, String cardNumber, String note) {
}
