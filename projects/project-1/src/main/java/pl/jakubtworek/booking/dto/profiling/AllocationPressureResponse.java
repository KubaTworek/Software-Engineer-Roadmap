package pl.jakubtworek.booking.dto.profiling;

public record AllocationPressureResponse(
        int objectsCreated,
        long approximatePayloadBytes,
        long elapsedMillis,
        String bottleneckToObserve
) {
}
