package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;

/**
 * Framework-neutral sketch of an HTTP tracing middleware.
 *
 * In Spring Boot, this responsibility is usually handled by OpenTelemetry Java Agent
 * or Spring instrumentation. This class shows the underlying idea explicitly.
 */
public final class HttpTracingMiddleware {

    private final CheckoutSpanFactory spanFactory;
    private final CheckoutPaymentTracingService paymentTracingService;

    public HttpTracingMiddleware(
            CheckoutSpanFactory spanFactory,
            CheckoutPaymentTracingService paymentTracingService
    ) {
        this.spanFactory = spanFactory;
        this.paymentTracingService = paymentTracingService;
    }

    public CheckoutPaymentTracingService.PaymentResult handlePayOrder(
            String requestIdHeader,
            String orderId
    ) {
        RequestCorrelation requestCorrelation =
                RequestCorrelation.fromHeaderOrGenerate(requestIdHeader);

        try (SpanScope spanScope = spanFactory.startPaymentServerSpan("POST", 200)) {
            spanScope.span().setAttribute("request.id", requestCorrelation.requestId());

            return paymentTracingService.payOrder(requestCorrelation, orderId);
        } catch (RuntimeException exception) {
            Span currentSpan = Span.current();
            currentSpan.setAttribute(TracingAttributes.HTTP_RESPONSE_STATUS_CODE, 500L);
            SpanErrorHandler.recordException(currentSpan, exception);
            throw exception;
        }
    }
}