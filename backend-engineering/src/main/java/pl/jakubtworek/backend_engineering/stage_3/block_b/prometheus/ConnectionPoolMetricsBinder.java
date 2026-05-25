package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Binds database connection pool saturation gauges.
 *
 * Pending requests are a saturation signal. They often explain why latency rises
 * even when CPU usage looks normal.
 */
public final class ConnectionPoolMetricsBinder {

    private final MeterRegistry meterRegistry;
    private final String serviceName;

    public ConnectionPoolMetricsBinder(MeterRegistry meterRegistry, String serviceName) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.serviceName = requireNonBlank(serviceName, "serviceName");
    }

    /**
     * Registers a gauge for pending connection acquisition requests.
     *
     * The supplier should return the current number of requests waiting for a connection.
     */
    public void bindPendingRequestsGauge(String poolName, Supplier<Number> pendingRequestsSupplier) {
        String normalizedPool = requireNonBlank(poolName, "poolName");

        MetricCardinalityGuard.validateLabelValue(MetricLabels.POOL, normalizedPool);

        Gauge.builder(
                        MetricNames.DB_CLIENT_CONNECTION_PENDING_REQUESTS,
                        pendingRequestsSupplier,
                        Supplier::get
                )
                .description("Current number of pending database connection acquisition requests.")
                .tag(MetricLabels.SERVICE, serviceName)
                .tag(MetricLabels.POOL, normalizedPool)
                .register(meterRegistry);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}