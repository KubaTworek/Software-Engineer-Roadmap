package pl.jakubtworek.booking.dto;

import pl.jakubtworek.booking.entity.ReservationStatus;

import java.util.UUID;

public record SpringPitfallReservationView(
        UUID reservationId,
        UUID eventId,
        String eventName,
        UUID customerId,
        String customerEmail,
        ReservationStatus status
) {
}
