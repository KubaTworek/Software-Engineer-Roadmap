package pl.jakubtworek.backend.reservation.api;

import pl.jakubtworek.backend.reservation.domain.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID eventId,
        String userId,
        int quantity,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
