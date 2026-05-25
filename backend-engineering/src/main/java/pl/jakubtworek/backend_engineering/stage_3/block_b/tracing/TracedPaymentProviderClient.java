package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

import java.util.HashMap;
import java.util.Map;

/**
 * Example traced client for an external payment provider.
 *
 * The trace context is injected into outbound headers using W3C traceparent.
 */
public final class TracedPaymentProviderClient {

    private final CheckoutSpanFactory spanFactory;
    private final TraceHeaderPropagator traceHeaderPropagator;
    private final PaymentProviderGateway paymentProviderGateway;

    public TracedPaymentProviderClient(
            CheckoutSpanFactory spanFactory,
            TraceHeaderPropagator traceHeaderPropagator,
            PaymentProviderGateway paymentProviderGateway
    ) {
        this.spanFactory = spanFactory;
        this.traceHeaderPropagator = traceHeaderPropagator;
        this.paymentProviderGateway = paymentProviderGateway;
    }

    public PaymentProviderResponse charge(
            RequestCorrelation requestCorrelation,
            String orderId,
            long amountCents,
            String currency
    ) {
        try (SpanScope spanScope = spanFactory.startPaymentProviderSpan("POST")) {
            Map<String, String> outboundHeaders =
                    traceHeaderPropagator.injectCurrentContextWithRequestId(
                            new HashMap<>(),
                            requestCorrelation
                    );

            PaymentProviderResponse response = paymentProviderGateway.charge(
                    outboundHeaders,
                    orderId,
                    amountCents,
                    currency
            );

            Span span = spanScope.span();
            span.setAttribute(TracingAttributes.HTTP_RESPONSE_STATUS_CODE, response.statusCode());

            if (response.statusCode() >= 500) {
                span.setStatus(StatusCode.ERROR, "payment provider server error");
            }

            return response;
        } catch (RuntimeException exception) {
            SpanErrorHandler.recordException(Span.current(), exception);
            throw exception;
        }
    }

    /**
     * Minimal abstraction over an HTTP client or SDK.
     */
    public interface PaymentProviderGateway {
        PaymentProviderResponse charge(
                Map<String, String> headers,
                String orderId,
                long amountCents,
                String currency
        );
    }

    public record PaymentProviderResponse(
            int statusCode,
            String body
    ) {
    }
}