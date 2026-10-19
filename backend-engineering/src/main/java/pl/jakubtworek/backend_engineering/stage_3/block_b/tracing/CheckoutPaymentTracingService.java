package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import java.util.Objects;

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
    private final OrderCacheCodec orderCacheCodec;

    public CheckoutPaymentTracingService(
            CheckoutSpanFactory spanFactory,
            TracedRedisClient redisClient,
            TracedOrderRepository orderRepository,
            TracedPaymentProviderClient paymentProviderClient
    ) {
        this(spanFactory, redisClient, orderRepository, paymentProviderClient, new OrderCacheCodec());
    }

    public CheckoutPaymentTracingService(
            CheckoutSpanFactory spanFactory,
            TracedRedisClient redisClient,
            TracedOrderRepository orderRepository,
            TracedPaymentProviderClient paymentProviderClient,
            OrderCacheCodec orderCacheCodec
    ) {
        this.spanFactory = Objects.requireNonNull(spanFactory, "spanFactory must not be null");
        this.redisClient = Objects.requireNonNull(redisClient, "redisClient must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.paymentProviderClient = Objects.requireNonNull(
                paymentProviderClient,
                "paymentProviderClient must not be null"
        );
        this.orderCacheCodec = Objects.requireNonNull(orderCacheCodec, "orderCacheCodec must not be null");
    }

    public PaymentResult payOrder(RequestCorrelation requestCorrelation, String orderId) {
        Objects.requireNonNull(requestCorrelation, "requestCorrelation must not be null");
        String validatedOrderId = requireNonBlank(orderId, "orderId");

        try (SpanScope spanScope = spanFactory.startChargeOrderSpan(validatedOrderId)) {
            try {
                String cacheKey = "order:" + validatedOrderId;

                String cachedOrder = redisClient.getOrderFromCache(cacheKey);

                TracedOrderRepository.OrderRecord order;
                if (cachedOrder == null) {
                    order = orderRepository.findOrder(validatedOrderId);
                } else {
                    order = orderCacheCodec.decode(cachedOrder);
                    if (!order.id().equals(validatedOrderId)) {
                        throw new IllegalStateException("cached order does not match the requested order id");
                    }
                }

                TracedPaymentProviderClient.PaymentProviderResponse providerResponse =
                        paymentProviderClient.charge(
                                requestCorrelation,
                                order.id(),
                                order.totalCents(),
                                order.currency()
                        );

                if (providerResponse.statusCode() >= 500) {
                    throw new RuntimeException("payment provider failed");
                }
                if (providerResponse.statusCode() < 200 || providerResponse.statusCode() >= 300) {
                    return new PaymentResult(false, order.id());
                }

                return new PaymentResult(true, order.id());
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

    public record PaymentResult(
            boolean ok,
            String orderId
    ) {
    }
}
