package pl.jakubtworek.booking.dto.profiling;

import java.util.UUID;

public record ProfilingReservationResponse(
        UUID eventId,
        int requestedReservations,
        int successfulReservations,
        int failedReservations,
        long elapsedMillis,
        double throughputPerSecond,
        String bottleneckToObserve
) {
}
