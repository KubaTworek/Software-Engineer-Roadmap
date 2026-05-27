package pl.jakubtworek.booking.dto;

import pl.jakubtworek.booking.entity.EventStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        String city,
        String category,
        OffsetDateTime startsAt,
        EventStatus status,
        int totalCapacity,
        int availableCapacity
) {
}
