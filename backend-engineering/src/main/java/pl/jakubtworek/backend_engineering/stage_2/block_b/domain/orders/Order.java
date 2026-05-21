package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.orders;

import java.math.BigDecimal;
import java.util.List;

/**
 * Domain object representing an order.
 *
 * This class belongs to the Order Service boundary.
 * Other services should not directly depend on this internal domain model.
 * They should consume public event contracts instead.
 */
public class Order {

    private final String orderId;
    private final List<OrderItem> items;
    private final BigDecimal totalAmount;
    private OrderStatus status;

    public Order(
            String orderId,
            List<OrderItem> items,
            BigDecimal totalAmount
    ) {
        this.orderId = orderId;
        this.items = List.copyOf(items);
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    /**
     * Marks the order as confirmed after successful payment.
     */
    public void confirm() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only orders pending payment can be confirmed.");
        }

        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * Cancels the order, usually as a result of failed payment or compensation logic.
     */
    public void cancel() {
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("Shipped orders cannot be cancelled.");
        }

        this.status = OrderStatus.CANCELLED;
    }

    public String orderId() {
        return orderId;
    }

    public List<OrderItem> items() {
        return items;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public OrderStatus status() {
        return status;
    }
}