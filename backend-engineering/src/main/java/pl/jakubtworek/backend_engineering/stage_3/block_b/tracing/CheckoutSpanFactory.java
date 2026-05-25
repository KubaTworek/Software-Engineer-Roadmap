package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

import java.util.Objects;

/**
 * Creates manual spans for checkout-api.
 *
 * Auto-instrumentation should still cover framework and client edges.
 * This factory adds domain and dependency spans where explicit semantics matter.
 */
public final class CheckoutSpanFactory {

    private final Tracer tracer;

    public CheckoutSpanFactory(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
    }

    /**
     * Starts the inbound HTTP server span.
     *
     * In many frameworks this span is created by auto-instrumentation.
     * Use this method only when the framework does not create a proper SERVER span.
     */
    public SpanScope startPaymentServerSpan(String method, int statusCode) {
        SpanBuilder spanBuilder = tracer.spanBuilder(TracingConstants.PAYMENT_SERVER_SPAN_NAME)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(TracingAttributes.HTTP_REQUEST_METHOD, method)
                .setAttribute(TracingAttributes.HTTP_ROUTE, TracingConstants.PAYMENT_ROUTE)
                .setAttribute(TracingAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);

        Span span = spanBuilder.startSpan();
        return new SpanScope(span, span.makeCurrent());
    }

    /**
     * Starts a domain span around the core payment use case.
     *
     * Domain spans should describe business-relevant operations,
     * not low-level technical calls already covered by auto-instrumentation.
     */
    public SpanScope startChargeOrderSpan(String orderId) {
        Span span = tracer.spanBuilder(TracingConstants.DOMAIN_CHARGE_ORDER_SPAN_NAME)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(TracingAttributes.APP_ORDER_ID, orderId)
                .startSpan();

        return new SpanScope(span, span.makeCurrent());
    }

    /**
     * Starts a Redis client span for a cache GET operation.
     */
    public SpanScope startRedisGetSpan(String namespace) {
        Span span = tracer.spanBuilder(TracingConstants.REDIS_GET_SPAN_NAME)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(TracingAttributes.DB_SYSTEM_NAME, TracingConstants.REDIS_SYSTEM_NAME)
                .setAttribute(TracingAttributes.DB_OPERATION_NAME, "GET")
                .setAttribute(TracingAttributes.SERVER_ADDRESS, TracingConstants.REDIS_SERVER_ADDRESS)
                .setAttribute(TracingAttributes.DB_NAMESPACE, namespace)
                .startSpan();

        return new SpanScope(span, span.makeCurrent());
    }

    /**
     * Starts a PostgreSQL client span with a low-cardinality query summary.
     *
     * Do not use full SQL as the span name. Full SQL can leak sensitive data
     * and produce poor grouping in tracing backends.
     */
    public SpanScope startPostgresSelectOrdersSpan() {
        Span span = tracer.spanBuilder(TracingConstants.POSTGRES_SELECT_ORDERS_SPAN_NAME)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(TracingAttributes.DB_SYSTEM_NAME, TracingConstants.POSTGRESQL_SYSTEM_NAME)
                .setAttribute(TracingAttributes.DB_QUERY_SUMMARY, "SELECT orders")
                .setAttribute(TracingAttributes.SERVER_ADDRESS, TracingConstants.POSTGRES_SERVER_ADDRESS)
                .setAttribute(TracingAttributes.SERVER_PORT, 5432L)
                .startSpan();

        return new SpanScope(span, span.makeCurrent());
    }

    /**
     * Starts an outbound HTTP client span for the payment provider.
     *
     * Standard HTTP client instrumentation may create this automatically.
     * Manual spans are useful when the client is custom or when attributes need to be normalized.
     */
    public SpanScope startPaymentProviderSpan(String method) {
        Span span = tracer.spanBuilder(TracingConstants.PAYMENT_PROVIDER_POST_SPAN_NAME)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(TracingAttributes.HTTP_REQUEST_METHOD, method)
                .setAttribute(TracingAttributes.SERVER_ADDRESS, TracingConstants.PAYMENT_PROVIDER_ADDRESS)
                .startSpan();

        return new SpanScope(span, span.makeCurrent());
    }
}