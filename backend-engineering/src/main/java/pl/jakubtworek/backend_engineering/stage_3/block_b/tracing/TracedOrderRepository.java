package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import java.util.Objects;

/**
 * Example repository wrapper with a PostgreSQL span.
 *
 * The span name and db.query.summary are intentionally low-cardinality.
 * The full SQL statement should not be used as a span name.
 */
public final class TracedOrderRepository {

    private final CheckoutSpanFactory spanFactory;
    private final OrderRepository orderRepository;

    public TracedOrderRepository(CheckoutSpanFactory spanFactory, OrderRepository orderRepository) {
        this.spanFactory = Objects.requireNonNull(spanFactory, "spanFactory must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
    }

    public OrderRecord findOrder(String orderId) {
        try (SpanScope spanScope = spanFactory.startPostgresSelectOrdersSpan()) {
            try {
                return orderRepository.findOrder(requireNonBlank(orderId, "orderId"));
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
     * Minimal repository abstraction used to avoid coupling tracing code to JDBC, JPA, or R2DBC.
     */
    public interface OrderRepository {
        OrderRecord findOrder(String orderId);
    }

    public record OrderRecord(
            String id,
            long totalCents,
            String currency
    ) {
        public OrderRecord {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            if (totalCents < 0) {
                throw new IllegalArgumentException("totalCents must not be negative");
            }
            if (currency == null || currency.isBlank()) {
                throw new IllegalArgumentException("currency must not be blank");
            }
        }
    }
}
