package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

/**
 * Minimal wiring example for the tracing components.
 *
 * In a real service, dependency injection would usually create these objects.
 */
public final class TracingExample {

    public static void main(String[] args) {
        OpenTelemetry openTelemetry = TracingConfiguration.createOpenTelemetry(
                "http://otel-collector:4317"
        );

        Tracer tracer = TracingConfiguration.createTracer(openTelemetry);

        CheckoutSpanFactory spanFactory = new CheckoutSpanFactory(tracer);
        TraceHeaderPropagator traceHeaderPropagator = new TraceHeaderPropagator(openTelemetry);

        TracedRedisClient redisClient = new TracedRedisClient(
                spanFactory,
                key -> null
        );

        TracedOrderRepository orderRepository = new TracedOrderRepository(
                spanFactory,
                orderId -> new TracedOrderRepository.OrderRecord(orderId, 2599L, "PLN")
        );

        TracedPaymentProviderClient paymentProviderClient = new TracedPaymentProviderClient(
                spanFactory,
                traceHeaderPropagator,
                (headers, orderId, amountCents, currency) ->
                        new TracedPaymentProviderClient.PaymentProviderResponse(200, "{\"ok\":true}")
        );

        CheckoutPaymentTracingService paymentService =
                new CheckoutPaymentTracingService(
                        spanFactory,
                        redisClient,
                        orderRepository,
                        paymentProviderClient
                );

        HttpTracingMiddleware middleware =
                new HttpTracingMiddleware(spanFactory, paymentService);

        CheckoutPaymentTracingService.PaymentResult result =
                middleware.handlePayOrder(
                        "req-01JV3D9Q4PSZ3BXKZ3WN9Q5D1K",
                        "order-123"
                );

        System.out.println(result);
    }
}