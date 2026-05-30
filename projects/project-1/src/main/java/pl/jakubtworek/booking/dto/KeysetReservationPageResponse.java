package pl.jakubtworek.booking.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KeysetReservationPageResponse(
        List<ReservationListItemResponse> items,
        Instant nextAfterCreatedAt,
        UUID nextAfterId,
        boolean hasNext
) {
}
