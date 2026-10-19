package pl.jakubtworek.backend_engineering.stage_3.block_b.pipeline;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.MetricCardinalityBudget;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.MetricLabels;
import pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus.RouteTemplate;
import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.CorrelationContext;
import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.HttpLogEvents;
import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.ServiceResource;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.RequestCorrelation;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.SpanErrorHandler;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.TraceContextSnapshot;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.TraceHeaderPropagator;
import pl.jakubtworek.backend_engineering.stage_3.block_b.tracing.TracingAttributes;

import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Small end-to-end telemetry path: inbound propagation, SERVER and CLIENT
 * spans, correlated structured log and histogram measurement.
 *
 * Framework agents normally create HTTP/client spans automatically. This class
 * keeps the mechanics visible for the laboratory and must not be installed next
 * to equivalent auto-instrumentation in production.
 */
public final class CheckoutTelemetryPipeline {

    public static final String REQUEST_DURATION_METRIC = "checkout.http.server.duration";
    private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");
    private static final AttributeKey<String> HTTP_METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> STATUS_CLASS = AttributeKey.stringKey("http.response.status_class");

    private final Tracer tracer;
    private final TraceHeaderPropagator propagator;
    private final DoubleHistogram requestDuration;
    private final HttpLogEvents httpLogEvents;
    private final TelemetryLogSink logSink;
    private final PaymentDependency paymentDependency;
    private final MetricCardinalityBudget cardinalityBudget;
    private final LongSupplier nanoTime;

    public CheckoutTelemetryPipeline(
            OpenTelemetry openTelemetry,
            ServiceResource serviceResource,
            TelemetryLogSink logSink,
            PaymentDependency paymentDependency,
            MetricCardinalityBudget cardinalityBudget
    ) {
        this(openTelemetry, serviceResource, logSink, paymentDependency,
                cardinalityBudget, System::nanoTime);
    }

    CheckoutTelemetryPipeline(
            OpenTelemetry openTelemetry,
            ServiceResource serviceResource,
            TelemetryLogSink logSink,
            PaymentDependency paymentDependency,
            MetricCardinalityBudget cardinalityBudget,
            LongSupplier nanoTime
    ) {
        OpenTelemetry telemetry = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
        this.tracer = telemetry.getTracer("checkout-telemetry-pipeline");
        this.propagator = new TraceHeaderPropagator(telemetry);
        this.requestDuration = telemetry.getMeter("checkout-telemetry-pipeline")
                .histogramBuilder(REQUEST_DURATION_METRIC)
                .setDescription("Duration of checkout HTTP server requests.")
                .setUnit("s")
                .build();
        this.httpLogEvents = new HttpLogEvents(
                Objects.requireNonNull(serviceResource, "serviceResource must not be null"));
        this.logSink = Objects.requireNonNull(logSink, "logSink must not be null");
        this.paymentDependency = Objects.requireNonNull(
                paymentDependency, "paymentDependency must not be null");
        this.cardinalityBudget = Objects.requireNonNull(
                cardinalityBudget, "cardinalityBudget must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    public void payOrder(Map<String, String> inboundHeaders, RouteTemplate route, String orderId) {
        Objects.requireNonNull(route, "route must not be null");
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }

        cardinalityBudget.record(MetricLabels.ROUTE, route.value());
        cardinalityBudget.record(MetricLabels.METHOD, "POST");
        Context parent = propagator.extractContext(inboundHeaders);
        RequestCorrelation request = RequestCorrelation.fromHeaderOrGenerate(
                headerIgnoreCase(inboundHeaders, "x-request-id"));
        long startedAt = nanoTime.getAsLong();
        int statusCode = 200;

        Span serverSpan = tracer.spanBuilder("POST " + route.value())
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(TracingAttributes.HTTP_REQUEST_METHOD, "POST")
                .setAttribute(TracingAttributes.HTTP_ROUTE, route.value())
                .startSpan();

        try (Scope ignored = serverSpan.makeCurrent()) {
            callPaymentDependency(request, route, orderId);
            logSink.emit(httpLogEvents.requestCompleted(
                    correlation(request), "POST", route.value(), statusCode,
                    elapsedMillis(startedAt)));
        } catch (RuntimeException failure) {
            statusCode = 500;
            serverSpan.setAttribute(TracingAttributes.HTTP_RESPONSE_STATUS_CODE, 500L);
            SpanErrorHandler.recordException(serverSpan, failure);
            throw failure;
        } finally {
            serverSpan.setAttribute(TracingAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
            requestDuration.record(
                    elapsedSeconds(startedAt),
                    Attributes.of(
                            HTTP_ROUTE, route.value(),
                            HTTP_METHOD, "POST",
                            STATUS_CLASS, statusCode / 100 + "xx"));
            serverSpan.end();
        }
    }

    private void callPaymentDependency(
            RequestCorrelation request,
            RouteTemplate route,
            String orderId
    ) {
        Span dependencySpan = tracer.spanBuilder("POST payment-provider")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(TracingAttributes.SERVER_ADDRESS, "payments.example")
                .startSpan();
        try (Scope ignored = dependencySpan.makeCurrent()) {
            try {
                paymentDependency.charge(
                        orderId,
                        propagator.injectCurrentContextWithRequestId(Map.of(), request));
            } catch (RuntimeException failure) {
                SpanErrorHandler.recordException(dependencySpan, failure);
                logSink.emit(httpLogEvents.requestFailed(
                        correlation(request), "POST", route.value(), 500,
                        failure.getClass().getSimpleName(), 0));
                throw failure;
            }
        } finally {
            dependencySpan.end();
        }
    }

    private static CorrelationContext correlation(RequestCorrelation request) {
        TraceContextSnapshot trace = TraceContextSnapshot.current();
        return new CorrelationContext(request.requestId(), trace.traceId(), trace.spanId());
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, nanoTime.getAsLong() - startedAt) / 1_000_000;
    }

    private double elapsedSeconds(long startedAt) {
        return Math.max(0, nanoTime.getAsLong() - startedAt) / 1_000_000_000.0;
    }

    private static String headerIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
