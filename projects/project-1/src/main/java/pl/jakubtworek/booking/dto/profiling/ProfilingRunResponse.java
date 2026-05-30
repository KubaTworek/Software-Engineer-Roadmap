package pl.jakubtworek.booking.dto.profiling;

public record ProfilingRunResponse(
        String scenario,
        long operations,
        long elapsedMillis,
        double throughputPerSecond,
        String bottleneckToObserve
) {
    public static ProfilingRunResponse of(String scenario, long operations, long elapsedNanos, String bottleneckToObserve) {
        long elapsedMillis = Math.max(1L, elapsedNanos / 1_000_000L);
        double throughput = operations * 1_000_000_000.0 / Math.max(1L, elapsedNanos);
        return new ProfilingRunResponse(scenario, operations, elapsedMillis, throughput, bottleneckToObserve);
    }
}
