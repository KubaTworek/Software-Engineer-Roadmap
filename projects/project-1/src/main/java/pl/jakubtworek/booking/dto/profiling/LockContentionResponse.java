package pl.jakubtworek.booking.dto.profiling;

public record LockContentionResponse(
        int threads,
        int incrementsPerThread,
        long finalValue,
        long elapsedMillis,
        String bottleneckToObserve
) {
}
