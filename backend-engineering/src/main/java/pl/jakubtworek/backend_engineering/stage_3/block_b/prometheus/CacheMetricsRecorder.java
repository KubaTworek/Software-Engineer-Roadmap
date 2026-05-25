package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;

/**
 * Records cache metrics.
 *
 * Cache metrics should answer whether the cache is helping, failing,
 * timing out, or shifting load to the database.
 */
public final class CacheMetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final String serviceName;

    public CacheMetricsRecorder(MeterRegistry meterRegistry, String serviceName) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.serviceName = requireNonBlank(serviceName, "serviceName");
    }

    /**
     * Records one cache operation result.
     *
     * The operation label should be bounded, for example GET, SET, DEL, or EXISTS.
     */
    public void recordCacheRequest(String operation, CacheResult result) {
        String normalizedOperation = requireNonBlank(operation, "operation").toUpperCase();

        MetricCardinalityGuard.validateLabelValue(MetricLabels.OPERATION, normalizedOperation);

        Counter.builder(MetricNames.CACHE_REQUESTS_TOTAL)
                .description("Total number of cache requests by operation and result.")
                .tag(MetricLabels.SERVICE, serviceName)
                .tag(MetricLabels.OPERATION, normalizedOperation)
                .tag(MetricLabels.RESULT, result.labelValue())
                .register(meterRegistry)
                .increment();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}