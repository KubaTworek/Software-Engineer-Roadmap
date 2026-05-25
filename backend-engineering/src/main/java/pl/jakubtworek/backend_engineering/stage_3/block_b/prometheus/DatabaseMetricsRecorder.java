package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;

/**
 * Records database and database-pool metrics.
 *
 * Database latency should be measured separately from HTTP latency,
 * because downstream latency is often the real source of user-facing slowness.
 */
public final class DatabaseMetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final String serviceName;

    public DatabaseMetricsRecorder(MeterRegistry meterRegistry, String serviceName) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.serviceName = requireNonBlank(serviceName, "serviceName");
    }

    /**
     * Records duration of a database client operation.
     *
     * Operation should be low-cardinality, for example SELECT, INSERT, UPDATE, DELETE, GET, SET.
     * Never use full SQL as a label.
     */
    public void recordOperationDuration(
            String dbSystem,
            String operation,
            Duration duration
    ) {
        String normalizedDbSystem = requireNonBlank(dbSystem, "dbSystem").toLowerCase();
        String normalizedOperation = requireNonBlank(operation, "operation").toUpperCase();

        MetricCardinalityGuard.validateLabelValue(MetricLabels.DB_SYSTEM, normalizedDbSystem);
        MetricCardinalityGuard.validateLabelValue(MetricLabels.OPERATION, normalizedOperation);

        Timer.builder(MetricNames.DB_CLIENT_OPERATION_DURATION_SECONDS)
                .description("Database client operation duration in seconds.")
                .tag(MetricLabels.SERVICE, serviceName)
                .tag(MetricLabels.DB_SYSTEM, normalizedDbSystem)
                .tag(MetricLabels.OPERATION, normalizedOperation)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * Records a database connection pool timeout.
     *
     * This helps distinguish slow SQL from inability to acquire a connection.
     */
    public void recordConnectionTimeout(String poolName) {
        String normalizedPool = requireNonBlank(poolName, "poolName");

        MetricCardinalityGuard.validateLabelValue(MetricLabels.POOL, normalizedPool);

        Counter.builder(MetricNames.DB_CLIENT_CONNECTION_TIMEOUTS_TOTAL)
                .description("Total number of database connection acquisition timeouts.")
                .tag(MetricLabels.SERVICE, serviceName)
                .tag(MetricLabels.POOL, normalizedPool)
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