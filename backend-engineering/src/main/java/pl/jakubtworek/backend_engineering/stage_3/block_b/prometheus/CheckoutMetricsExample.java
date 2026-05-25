package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;

/**
 * Demonstrates how checkout-api could record Prometheus-compatible metrics.
 *
 * In production, MeterRegistry would usually be provided by Spring Boot Actuator
 * with Prometheus registry enabled.
 */
public final class CheckoutMetricsExample {

    public static void main(String[] args) {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        HttpMetricsRecorder httpMetrics = new HttpMetricsRecorder(
                meterRegistry,
                "checkout-api"
        );

        CacheMetricsRecorder cacheMetrics = new CacheMetricsRecorder(
                meterRegistry,
                "checkout-api"
        );

        DatabaseMetricsRecorder databaseMetrics = new DatabaseMetricsRecorder(
                meterRegistry,
                "checkout-api"
        );

        PaymentProviderMetricsRecorder paymentProviderMetrics =
                new PaymentProviderMetricsRecorder(
                        meterRegistry,
                        "checkout-api"
                );

        RouteTemplate paymentRoute = RouteTemplate.of("/orders/:id/pay");

        try (HttpMetricsRecorder.InflightRequestScope ignored =
                     httpMetrics.startInflightRequest(paymentRoute, "POST")) {

            cacheMetrics.recordCacheRequest("GET", CacheResult.MISS);

            databaseMetrics.recordOperationDuration(
                    "postgresql",
                    "SELECT",
                    Duration.ofMillis(42)
            );

            paymentProviderMetrics.recordProviderRequest(
                    "stripe",
                    200,
                    Duration.ofMillis(130)
            );

            httpMetrics.recordRequest(
                    paymentRoute,
                    "POST",
                    200,
                    Duration.ofMillis(183)
            );
        }
    }
}