package pl.jakubtworek.booking.service.async;

import pl.jakubtworek.booking.dto.ReservationResponse;

public record AsyncConfirmationResult(
        ReservationResponse reservation,
        DeliverySummary deliverySummary,
        SideEffectResult auditResult
) {
}
