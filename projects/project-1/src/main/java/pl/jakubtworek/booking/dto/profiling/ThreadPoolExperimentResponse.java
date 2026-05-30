package pl.jakubtworek.booking.dto.profiling;

public record ThreadPoolExperimentResponse(
        int threads,
        int tasks,
        String workloadType,
        long elapsedMillis,
        double throughputPerSecond,
        String bottleneckToObserve
) {
}
