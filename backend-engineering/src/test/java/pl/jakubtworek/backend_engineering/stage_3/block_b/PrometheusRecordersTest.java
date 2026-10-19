package pl.jakubtworek.backend_engineering.stage_3.block_b;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.CacheMetricsRecorder;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.DatabaseMetricsRecorder;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.HttpMetricsRecorder;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.MetricNames;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.PaymentProviderMetricsRecorder;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.RouteTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PrometheusRecordersTest {

    @Test
    void recordsAnHttpRequestUsingNormalizedBoundedLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpMetricsRecorder recorder = new HttpMetricsRecorder(registry, "checkout-api");
        RouteTemplate route = RouteTemplate.of("/orders/{orderId}/pay");

        recorder.recordRequest(route, "post", 201, Duration.ofMillis(25));

        assertThat(registry.get(MetricNames.HTTP_REQUESTS_TOTAL)
                .tag("method", "POST")
                .tag("status_code", "201")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.get(MetricNames.HTTP_REQUEST_DURATION_SECONDS)
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidHttpMeasurementsBeforeRegisteringMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpMetricsRecorder recorder = new HttpMetricsRecorder(registry, "checkout-api");
        RouteTemplate route = RouteTemplate.of("/orders/{orderId}/pay");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> recorder.recordRequest(route, "POST", 99, Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> recorder.recordRequest(route, "POST", 200, Duration.ofNanos(-1)));
        assertThatNullPointerException()
                .isThrownBy(() -> recorder.recordRequest(null, "POST", 200, Duration.ZERO));

        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void closesInflightScopeOnlyOnce() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpMetricsRecorder recorder = new HttpMetricsRecorder(registry, "checkout-api");

        HttpMetricsRecorder.InflightRequestScope scope = recorder.startInflightRequest(
                RouteTemplate.of("/orders/{orderId}/pay"),
                "POST"
        );
        scope.close();
        scope.close();

        assertThat(registry.get(MetricNames.HTTP_INFLIGHT_REQUESTS).gauge().value()).isZero();
    }

    @Test
    void validatesDependencyMeasurements() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DatabaseMetricsRecorder database = new DatabaseMetricsRecorder(registry, "checkout-api");
        PaymentProviderMetricsRecorder payment = new PaymentProviderMetricsRecorder(registry, "checkout-api");
        CacheMetricsRecorder cache = new CacheMetricsRecorder(registry, "checkout-api");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> database.recordOperationDuration("postgresql", "SELECT", Duration.ofNanos(-1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> payment.recordProviderRequest("stripe", 700, Duration.ZERO));
        assertThatNullPointerException()
                .isThrownBy(() -> cache.recordCacheRequest("GET", null));

        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void rejectsAnUnboundedServiceLabel() {
        String suspiciousServiceName = "x".repeat(121);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpMetricsRecorder(new SimpleMeterRegistry(), suspiciousServiceName));
    }
}
