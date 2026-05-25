package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configures histogram behavior for latency metrics.
 *
 * Histograms are required for Prometheus-side percentile calculations
 * with histogram_quantile(). Client-side averages are not enough for tail latency.
 */
@Configuration
public class CheckoutMetricsConfig {

    @Bean
    public MeterFilter checkoutHttpLatencyHistogramConfig() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(
                    Meter.Id id,
                    DistributionStatisticConfig config
            ) {
                if (MetricNames.HTTP_REQUEST_DURATION_SECONDS.equals(id.getName())
                        || MetricNames.DB_CLIENT_OPERATION_DURATION_SECONDS.equals(id.getName())
                        || MetricNames.PAYMENT_PROVIDER_DURATION_SECONDS.equals(id.getName())) {
                    return DistributionStatisticConfig.builder()
                            .serviceLevelObjectives(
                                    Duration.ofMillis(50).toNanos(),
                                    Duration.ofMillis(100).toNanos(),
                                    Duration.ofMillis(250).toNanos(),
                                    Duration.ofMillis(500).toNanos(),
                                    Duration.ofSeconds(1).toNanos(),
                                    Duration.ofSeconds(2).toNanos(),
                                    Duration.ofSeconds(5).toNanos()
                            )
                            .percentilesHistogram(true)
                            .build()
                            .merge(config);
                }

                return config;
            }
        };
    }
}