package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.event.OrderPaid;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.event.OrderPlaced;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

// Aggregate root for customer order.
// All changes to order lines and status must go through this root.
public final class Order extends EventSourcedAggregate<OrderId> {

    private final OrderId id;
    private final CustomerId customerId;
    private final List<OrderLine> lines;
    private OrderStatus status;
    private Money totalPrice;

    private Order(
            OrderId id,
            CustomerId customerId,
            List<OrderLine> lines,
            OrderStatus status,
            Money totalPrice
    ) {
        this.id = id;
        this.customerId = customerId;
        this.lines = new ArrayList<>(lines);
        this.status = status;
        this.totalPrice = totalPrice;
    }

    public static Order draft(OrderId id, CustomerId customerId, Currency currency) {
        return new Order(
                id,
                customerId,
                List.of(),
                OrderStatus.DRAFT,
                Money.zero(currency)
        );
    }

    // Adds a line to the order and recalculates the aggregate invariant.
    public void addLine(ProductId productId, Quantity quantity, Money unitPrice) {
        ensureDraft();

        OrderLine line = new OrderLine(
                UUID.randomUUID().toString(),
                productId,
                quantity,
                unitPrice
        );

        lines.add(line);
        recalculateTotalPrice();
    }

    // Places the order and emits a domain event.
    // The method protects business invariants before changing the state.
    public void place() {
        ensureDraft();

        if (lines.isEmpty()) {
            throw new IllegalStateException("Order must contain at least one line");
        }

        this.status = OrderStatus.PLACED;

        record(new OrderPlaced(
                UUID.randomUUID().toString(),
                Instant.now(),
                id,
                customerId,
                List.copyOf(lines),
                totalPrice
        ));
    }

    // Marks order as paid after receiving confirmation from Billing.
    public void markAsPaid(String paymentId) {
        if (status != OrderStatus.PLACED) {
            throw new IllegalStateException("Only placed order can be paid");
        }

        this.status = OrderStatus.PAID;

        record(new OrderPaid(
                UUID.randomUUID().toString(),
                Instant.now(),
                id,
                paymentId
        ));
    }

    // Cancels the order if business rules allow it.
    public void cancel() {
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("Shipped order cannot be cancelled");
        }

        this.status = OrderStatus.CANCELLED;
    }

    private void ensureDraft() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Only draft order can be modified");
        }
    }

    private void recalculateTotalPrice() {
        if (lines.isEmpty()) {
            return;
        }

        Money total = Money.zero(lines.get(0).unitPrice().currency());

        for (OrderLine line : lines) {
            total = total.add(line.totalPrice());
        }

        this.totalPrice = total;
    }

    @Override
    public OrderId id() {
        return id;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public List<OrderLine> lines() {
        return List.copyOf(lines);
    }

    public OrderStatus status() {
        return status;
    }

    public Money totalPrice() {
        return totalPrice;
    }
}