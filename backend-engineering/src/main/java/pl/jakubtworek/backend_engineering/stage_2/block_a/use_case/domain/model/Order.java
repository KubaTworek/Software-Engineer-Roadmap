package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.OrderPlacedEvent;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

// Aggregate root for the order write model.
// It owns order lines, protects invariants, changes state, and records domain events.
public final class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private final List<OrderLine> lines = new ArrayList<>();
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    private OrderStatus status;
    private Money total;

    private Order(OrderId id, CustomerId customerId, Currency currency) {
        this.id = id;
        this.customerId = customerId;
        this.status = OrderStatus.DRAFT;
        this.total = Money.zero(currency);
    }

    public static Order create(OrderId id, CustomerId customerId, Currency currency) {
        return new Order(id, customerId, currency);
    }

    // Adds an order line while the aggregate is still in draft state.
    public void addLine(ProductId productId, int quantity, Money unitPrice) {
        ensureDraft();

        OrderLine line = new OrderLine(productId, quantity, unitPrice);
        lines.add(line);
        recalculateTotal();
    }

    // Places the order and verifies expected total.
    // Business invariants are checked inside the aggregate, not in the application service.
    public void place(Money expectedTotal) {
        ensureDraft();

        if (lines.isEmpty()) {
            throw new IllegalStateException("Order must contain at least one line");
        }

        if (!total.equals(expectedTotal)) {
            throw new IllegalStateException("Expected total does not match calculated total");
        }

        this.status = OrderStatus.PLACED;

        record(OrderPlacedEvent.now(id, customerId, total));
    }

    // Returns events produced by this aggregate but not yet published.
    public List<DomainEvent> uncommittedEvents() {
        return List.copyOf(uncommittedEvents);
    }

    // Clears events after they have been safely stored or published.
    public void clearEvents() {
        uncommittedEvents.clear();
    }

    private void record(DomainEvent event) {
        uncommittedEvents.add(event);
    }

    private void ensureDraft() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Only draft order can be modified");
        }
    }

    private void recalculateTotal() {
        Money calculated = Money.zero(total.currency());

        for (OrderLine line : lines) {
            calculated = calculated.add(line.total());
        }

        this.total = calculated;
    }

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

    public Money total() {
        return total;
    }
}