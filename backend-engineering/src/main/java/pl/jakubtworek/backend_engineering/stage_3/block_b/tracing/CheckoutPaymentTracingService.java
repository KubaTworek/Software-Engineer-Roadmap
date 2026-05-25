package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;

/**
 * Demonstrates how manual tracing composes the checkout payment flow.
 *
 * Each potentially slow dependency has its own span, so a trace can explain
 * whether latency came from Redis, PostgreSQL, the payment provider, or domain logic.
 */
public final class CheckoutPaymentTracingService {

    private final CheckoutSpanFactory spanFactory;
    private final TracedRedisClient redisClient;
    private final TracedOrderRepository orderRepository;
    private final TracedPaymentProviderClient paymentProviderClient;

    public CheckoutPaymentTracingService(
            CheckoutSpanFactory spanFactory,
            TracedRedisClient redisClient,
            TracedOrderRepository orderRepository,
            TracedPaymentProviderClient paymentProviderClient
    ) {
        this.spanFactory = spanFactory;
        this.redisClient = redisClient;
        this.orderRepository = orderRepository;
        this.paymentProviderClient = paymentProviderClient;
    }

    public PaymentResult payOrder(RequestCorrelation requestCorrelation, String orderId) {
        try (SpanScope ignored = spanFactory.startChargeOrderSpan(orderId)) {
            String cacheKey = "order:" + orderId;

            String cachedOrder = redisClient.getOrderFromCache(cacheKey);

            TracedOrderRepository.OrderRecord order;
            if (cachedOrder == null) {
                order = orderRepository.findOrder(orderId);
            } else {
                order = decodeCachedOrder(cachedOrder);
            }

            TracedPaymentProviderClient.PaymentProviderResponse providerResponse =
                    paymentProviderClient.charge(
                            requestCorrelation,
                            order.id(),
                            order.totalCents(),
                            order.currency()
                    );

            if (providerResponse.statusCode() >= 500) {
                RuntimeException exception = new RuntimeException("payment provider failed");
                SpanErrorHandler.recordException(Span.current(), exception);
                throw exception;
            }

            return new PaymentResult(true, order.id());
        } catch (RuntimeException exception) {
            SpanErrorHandler.recordException(Span.current(), exception);
            throw exception;
        }
    }

    private TracedOrderRepository.OrderRecord decodeCachedOrder(String cachedOrder) {
        /**
         * This is only a placeholder.
         *
         * Real code should deserialize the cached value safely and validate its schema.
         */
        return new TracedOrderRepository.OrderRecord("cached-order", 1000L, "PLN");
    }

    public record PaymentResult(
            boolean ok,
            String orderId
    ) {
    }
}