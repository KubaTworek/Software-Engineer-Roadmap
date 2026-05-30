package pl.jakubtworek.booking.dto;

import java.util.UUID;

public record EventStatsResponse(
        UUID eventId,
        long totalReservations,
        long pendingReservations,
        long confirmedReservations,
        long cancelledReservations,
        long paymentTimeoutReservations
) {
}
