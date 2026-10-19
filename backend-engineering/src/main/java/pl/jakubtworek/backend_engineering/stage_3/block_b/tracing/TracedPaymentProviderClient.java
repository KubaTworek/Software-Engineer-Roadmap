package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
        this.spanFactory = Objects.requireNonNull(spanFactory, "spanFactory must not be null");
        this.traceHeaderPropagator = Objects.requireNonNull(
                traceHeaderPropagator,
                "traceHeaderPropagator must not be null"
        );
        this.paymentProviderGateway = Objects.requireNonNull(
                paymentProviderGateway,
                "paymentProviderGateway must not be null"
        );
    }

    public PaymentProviderResponse charge(
            RequestCorrelation requestCorrelation,
            String orderId,
            long amountCents,
            String currency
    ) {
        try (SpanScope spanScope = spanFactory.startPaymentProviderSpan("POST")) {
            try {
                Objects.requireNonNull(requestCorrelation, "requestCorrelation must not be null");
                String validatedOrderId = requireNonBlank(orderId, "orderId");
                String validatedCurrency = requireNonBlank(currency, "currency");
                if (amountCents < 0) {
                    throw new IllegalArgumentException("amountCents must not be negative");
                }

                Map<String, String> outboundHeaders =
                        traceHeaderPropagator.injectCurrentContextWithRequestId(
                                new HashMap<>(),
                                requestCorrelation
                        );

                PaymentProviderResponse response = Objects.requireNonNull(
                        paymentProviderGateway.charge(
                                outboundHeaders,
                                validatedOrderId,
                                amountCents,
                                validatedCurrency
                        ),
                        "paymentProviderGateway response must not be null"
                );

                Span span = spanScope.span();
                span.setAttribute(TracingAttributes.HTTP_RESPONSE_STATUS_CODE, response.statusCode());

                if (response.statusCode() >= 500) {
                    span.setStatus(StatusCode.ERROR, "payment provider server error");
                }

                return response;
            } catch (RuntimeException exception) {
                SpanErrorHandler.recordException(spanScope.span(), exception);
                throw exception;
            }
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
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
        public PaymentProviderResponse {
            if (statusCode < 100 || statusCode > 599) {
                throw new IllegalArgumentException("statusCode must be between 100 and 599");
            }
        }
    }
}
