package pl.jakubtworek.booking.dto;

import pl.jakubtworek.booking.entity.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID eventId,
        String eventName,
        UUID customerId,
        String customerEmail,
        ReservationStatus status,
        Instant createdAt,
        Instant confirmedAt,
        Instant cancelledAt
) {
}
