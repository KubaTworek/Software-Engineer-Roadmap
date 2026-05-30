package pl.jakubtworek.booking.dto;

import pl.jakubtworek.booking.entity.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationListItemResponse(
        UUID id,
        UUID eventId,
        String eventName,
        UUID customerId,
        String customerEmail,
        ReservationStatus status,
        Instant createdAt
) {
}
