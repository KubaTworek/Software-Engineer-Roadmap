package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records HTTP golden-signal metrics for checkout-api.
 *
 * It captures traffic, errors through status codes, latency through histograms,
 * and saturation through inflight request gauges.
 */
public final class HttpMetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final String serviceName;

    private final ConcurrentHashMap<String, AtomicInteger> inflightByRouteAndMethod =
            new ConcurrentHashMap<>();

    public HttpMetricsRecorder(MeterRegistry meterRegistry, String serviceName) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.serviceName = requireNonBlank(serviceName, "serviceName");
    }

    /**
     * Starts tracking an inflight HTTP request.
     *
     * The returned scope must be closed when request processing finishes.
     */
    public InflightRequestScope startInflightRequest(RouteTemplate route, String method) {
        String normalizedMethod = normalizeMethod(method);
        String key = route.value() + "|" + normalizedMethod;

        AtomicInteger gaugeValue = inflightByRouteAndMethod.computeIfAbsent(key, ignored -> {
            AtomicInteger value = new AtomicInteger(0);

            Gauge.builder(
                            MetricNames.HTTP_INFLIGHT_REQUESTS,
                            value,
                            AtomicInteger::get
                    )
                    .description("Current number of inflight HTTP requests.")
                    .tag(MetricLabels.SERVICE, serviceName)
                    .tag(MetricLabels.ROUTE, route.value())
                    .tag(MetricLabels.METHOD, normalizedMethod)
                    .register(meterRegistry);

            return value;
        });

        gaugeValue.incrementAndGet();

        return new InflightRequestScope(gaugeValue);
    }

    /**
     * Records a completed HTTP request.
     *
     * Latency is recorded as a timer, which Micrometer can export as Prometheus histogram buckets.
     */
    public void recordRequest(
            RouteTemplate route,
            String method,
            int statusCode,
            Duration duration
    ) {
        String normalizedMethod = normalizeMethod(method);
        String statusCodeLabel = String.valueOf(statusCode);

        Counter.builder(MetricNames.HTTP_REQUESTS_TOTAL)
                .description("Total number of HTTP requests handled by checkout-api.")
                .tag(MetricLabels.SERVICE, serviceName)
                .tag(MetricLabels.ROUTE, route.value())
                .tag(MetricLabels.METHOD, normalizedMethod)
                .tag(MetricLabels.STATUS_CODE, statusCodeLabel)
                .register(meterRegistry)
                .increment();

        Timer.builder(MetricNames.HTTP_REQUEST_DURATION_SECONDS)
                .description("HTTP request duration in seconds.")
                .tag(MetricLabels.SERVICE, serviceName)
                .tag(MetricLabels.ROUTE, route.value())
                .tag(MetricLabels.METHOD, normalizedMethod)
                .tag(MetricLabels.STATUS_CODE, statusCodeLabel)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(duration);
    }

    private static String normalizeMethod(String method) {
        String value = requireNonBlank(method, "method").toUpperCase();

        MetricCardinalityGuard.validateLabelValue(MetricLabels.METHOD, value);

        return value;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * Represents an inflight request lifetime.
     *
     * Use try-with-resources to guarantee decrementing the gauge.
     */
    public static final class InflightRequestScope implements AutoCloseable {

        private final AtomicInteger gaugeValue;
        private boolean closed;

        private InflightRequestScope(AtomicInteger gaugeValue) {
            this.gaugeValue = gaugeValue;
        }

        @Override
        public void close() {
            if (!closed) {
                gaugeValue.decrementAndGet();
                closed = true;
            }
        }
    }
}