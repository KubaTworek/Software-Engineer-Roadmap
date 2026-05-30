package pl.jakubtworek.booking.dto.nosql;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record EventSearchDocumentResponse(
        UUID eventId,
        String name,
        String city,
        String category,
        OffsetDateTime startsAt,
        UUID organizationId,
        String organizationName,
        int totalCapacity,
        int availableCapacity,
        Map<String, Long> reservationsByStatus,
        Instant rebuiltAt
) {
}
