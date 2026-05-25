package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;

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
        this.spanFactory = spanFactory;
        this.orderRepository = orderRepository;
    }

    public OrderRecord findOrder(String orderId) {
        try (SpanScope ignored = spanFactory.startPostgresSelectOrdersSpan()) {
            return orderRepository.findOrder(orderId);
        } catch (RuntimeException exception) {
            SpanErrorHandler.recordException(Span.current(), exception);
            throw exception;
        }
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
    }
}