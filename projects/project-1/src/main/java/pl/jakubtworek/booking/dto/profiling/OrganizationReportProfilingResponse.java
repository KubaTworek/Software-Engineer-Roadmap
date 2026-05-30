package pl.jakubtworek.booking.dto.profiling;

import java.util.Map;
import java.util.UUID;

public record OrganizationReportProfilingResponse(
        UUID organizationId,
        long totalReservations,
        Map<String, Long> reservationsByStatus,
        long elapsedMillis,
        String bottleneckToObserve
) {
}
