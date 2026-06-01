package pl.jakubtworek.marketplace.ordering.domain;

import pl.jakubtworek.marketplace.shared.kernel.AggregateRoot;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.List;
import java.util.UUID;

public class Order extends AggregateRoot {
    private final OrderId id;
    private final CustomerId customerId;
    private final List<OrderLine> lines;
    private OrderStatus status;
    private boolean paymentReserved;
    private boolean stockReserved;

    private Order(OrderId id, CustomerId customerId, List<OrderLine> lines, OrderStatus status, boolean paymentReserved, boolean stockReserved) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("order must have at least one line");
        this.id = id;
        this.customerId = customerId;
        this.lines = List.copyOf(lines);
        this.status = status;
        this.paymentReserved = paymentReserved;
        this.stockReserved = stockReserved;
    }

    public static Order place(CustomerId customerId, List<OrderLine> lines, UUID correlationId) {
        Order order = new Order(OrderId.newId(), customerId, lines, OrderStatus.PLACED, false, false);
        order.registerEvent(OrderPlaced.now(order, correlationId, null));
        return order;
    }

    public static Order restore(OrderId id, CustomerId customerId, List<OrderLine> lines, OrderStatus status, boolean paymentReserved, boolean stockReserved) {
        return new Order(id, customerId, lines, status, paymentReserved, stockReserved);
    }

    public void markPaymentReserved(UUID correlationId, UUID causationId) {
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) return;
        this.paymentReserved = true;
        confirmIfReady(correlationId, causationId);
    }

    public void markStockReserved(UUID correlationId, UUID causationId) {
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) return;
        this.stockReserved = true;
        confirmIfReady(correlationId, causationId);
    }

    public void reject() {
        if (status == OrderStatus.COMPLETED) throw new IllegalStateException("completed order cannot be rejected");
        this.status = OrderStatus.REJECTED;
    }

    public void cancel(UUID correlationId, UUID causationId) {
        if (status == OrderStatus.COMPLETED) throw new IllegalStateException("completed order cannot be cancelled");
        if (status == OrderStatus.CANCELLED) return;
        this.status = OrderStatus.CANCELLED;
        registerEvent(OrderCancelled.now(this, correlationId, causationId));
    }

    private void confirmIfReady(UUID correlationId, UUID causationId) {
        if (paymentReserved && stockReserved && status != OrderStatus.CONFIRMED) {
            this.status = OrderStatus.CONFIRMED;
            registerEvent(OrderConfirmed.now(this, correlationId, causationId));
        }
    }

    public Money total() {
        return lines.stream().map(OrderLine::total).reduce(Money::add).orElseThrow();
    }

    public OrderId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public List<OrderLine> lines() { return lines; }
    public OrderStatus status() { return status; }
    public boolean paymentReserved() { return paymentReserved; }
    public boolean stockReserved() { return stockReserved; }
}
