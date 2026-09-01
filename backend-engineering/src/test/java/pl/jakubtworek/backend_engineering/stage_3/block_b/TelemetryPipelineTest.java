package pl.jakubtworek.backend_engineering.stage_3.block_b;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_b.pipeline.CheckoutTelemetryPipeline;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.MetricCardinalityBudget;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.RouteTemplate;
import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.ServiceResource;
import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.StructuredLogEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelemetryPipelineTest {

    private static final String INBOUND_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String INBOUND_PARENT_SPAN_ID = "00f067aa0ba902b7";

    private InMemorySpanExporter spanExporter;
    private InMemoryMetricReader metricReader;
    private SdkTracerProvider tracerProvider;
    private SdkMeterProvider meterProvider;
    private OpenTelemetry openTelemetry;

    @BeforeEach
    void createInMemoryTelemetrySdk() {
        spanExporter = InMemorySpanExporter.create();
        metricReader = InMemoryMetricReader.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .setPropagators(ContextPropagators.create(
                        W3CTraceContextPropagator.getInstance()))
                .build();
    }

    @AfterEach
    void closeTelemetrySdk() {
        meterProvider.close();
        tracerProvider.close();
    }

    @Test
    void connectsInboundTraceDependencyErrorStructuredLogAndLatencyHistogram() {
        List<StructuredLogEvent> logs = new ArrayList<>();
        AtomicReference<Map<String, String>> providerHeaders = new AtomicReference<>();
        RuntimeException providerFailure = new RuntimeException("provider timeout");
        CheckoutTelemetryPipeline pipeline = new CheckoutTelemetryPipeline(
                openTelemetry,
                serviceResource(),
                logs::add,
                (ignored, headers) -> {
                    providerHeaders.set(headers);
                    throw providerFailure;
                },
                new MetricCardinalityBudget(10)
        );

        Map<String, String> inboundHeaders = Map.of(
                "TraceParent", "00-" + INBOUND_TRACE_ID + "-" + INBOUND_PARENT_SPAN_ID + "-01",
                "X-Request-Id", "req-telemetry-42"
        );

        assertThatThrownBy(() -> pipeline.payOrder(
                inboundHeaders,
                RouteTemplate.of("/orders/{orderId}/pay"),
                "order-42"
        )).isSameAs(providerFailure);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(2);
        SpanData serverSpan = spanNamed(spans, "POST /orders/{orderId}/pay");
        SpanData dependencySpan = spanNamed(spans, "POST payment-provider");

        assertThat(serverSpan.getTraceId()).isEqualTo(INBOUND_TRACE_ID);
        assertThat(serverSpan.getParentSpanId()).isEqualTo(INBOUND_PARENT_SPAN_ID);
        assertThat(dependencySpan.getTraceId()).isEqualTo(INBOUND_TRACE_ID);
        assertThat(dependencySpan.getParentSpanId()).isEqualTo(serverSpan.getSpanId());
        assertThat(dependencySpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(dependencySpan.getEvents())
                .extracting(event -> event.getName())
                .contains("exception");
        assertThat(serverSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);

        assertThat(providerHeaders.get().get("traceparent"))
                .isEqualTo("00-" + INBOUND_TRACE_ID + "-" + dependencySpan.getSpanId() + "-01");
        assertThat(providerHeaders.get().get("x-request-id")).isEqualTo("req-telemetry-42");

        assertThat(logs).singleElement().satisfies(log -> {
            assertThat(log.fields().get("trace_id")).isEqualTo(INBOUND_TRACE_ID);
            assertThat(log.fields().get("span_id")).isEqualTo(dependencySpan.getSpanId());
            assertThat(log.fields().get("request_id")).isEqualTo("req-telemetry-42");
            assertThat(log.fields().get("error.type")).isEqualTo("RuntimeException");
        });

        MetricData duration = metricReader.collectAllMetrics().stream()
                .filter(metric -> metric.getName().equals(
                        CheckoutTelemetryPipeline.REQUEST_DURATION_METRIC))
                .findFirst()
                .orElseThrow();
        assertThat(duration.getType()).isEqualTo(MetricDataType.HISTOGRAM);
        assertThat(duration.getHistogramData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getCount()).isOne();
            assertThat(point.getSum()).isGreaterThanOrEqualTo(0.0);
            assertThat(point.getAttributes().get(AttributeKey.stringKey("http.route")))
                    .isEqualTo("/orders/{orderId}/pay");
            assertThat(point.getAttributes().get(AttributeKey.stringKey("http.response.status_class")))
                    .isEqualTo("5xx");
        });
    }

    @Test
    void cardinalityBudgetRejectsNewValuesButAllowsKnownSeries() {
        MetricCardinalityBudget budget = new MetricCardinalityBudget(2);

        budget.record("route", "/orders/{orderId}");
        budget.record("route", "/payments/{paymentId}");
        budget.record("route", "/orders/{orderId}");

        assertThatThrownBy(() -> budget.record("route", "/users/{userId}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cardinality budget");
        assertThat(budget.distinctValues("route")).isEqualTo(2);
        assertThatThrownBy(() -> budget.record("trace_id", INBOUND_TRACE_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SpanData spanNamed(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(span -> span.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ServiceResource serviceResource() {
        return ServiceResource.builder()
                .serviceName("checkout-api")
                .serviceVersion("1.0.0")
                .deploymentEnvironmentName("test")
                .serviceInstanceId("instance-1")
                .build();
    }
}
