package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;

/**
 * Records metrics for external payment provider calls.
 *
 * External provider metrics should use bounded labels such as provider and status_class.
 * Do not label by payment_id, customer_id, card_id, or raw error message.
 */
public final class PaymentProviderMetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final String serviceName;

    public PaymentProviderMetricsRecorder(MeterRegistry meterRegistry, String serviceName) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.serviceName = requireNonBlank(serviceName, "serviceName");
    }

    /**
     * Records one external payment provider request.
     */
    public void recordProviderRequest(
            String provider,
            int statusCode,
            Duration duration
    ) {
        String normalizedProvider = requireNonBlank(provider, "provider").toLowerCase();
        String statusClass = StatusClass.fromStatusCode(statusCode).labelValue();

        MetricCardinalityGuard.validateLabelValue(MetricLabels.PROVIDER, normalizedProvider);

        Counter.builder(MetricNames.PAYMENT_PROVIDER_REQUESTS_TOTAL)
                .description("Total number of payment provider requests.")
                .tag(MetricLabels.SERVICE, serviceName)
                .tag(MetricLabels.PROVIDER, normalizedProvider)
                .tag(MetricLabels.STATUS_CLASS, statusClass)
                .register(meterRegistry)
                .increment();

        Timer.builder(MetricNames.PAYMENT_PROVIDER_DURATION_SECONDS)
                .description("Payment provider request duration in seconds.")
                .tag(MetricLabels.SERVICE, serviceName)
                .tag(MetricLabels.PROVIDER, normalizedProvider)
                .tag(MetricLabels.STATUS_CLASS, statusClass)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(duration);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}