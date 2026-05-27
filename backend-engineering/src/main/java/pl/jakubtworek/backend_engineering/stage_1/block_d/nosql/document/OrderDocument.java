package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Przykład dokumentu zamówienia w stylu MongoDB.
 *
 * Document DB często przechowuje razem dane, które są razem odczytywane.
 * Order zawiera items oraz wybrane dane użytkownika i produktów.
 *
 * To jest świadoma denormalizacja:
 * - userEmail jest skopiowany do zamówienia,
 * - productName i unitPrice są skopiowane do items.
 */
public class OrderDocument {

    private final String id;
    private final String userId;
    private final String userEmail;
    private final OrderStatus status;
    private final List<OrderItemDocument> items;
    private final BigDecimal totalAmount;
    private final Instant createdAt;
    private final Instant updatedAt;

    public OrderDocument(
            String id,
            String userId,
            String userEmail,
            OrderStatus status,
            List<OrderItemDocument> items,
            BigDecimal totalAmount,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.userEmail = userEmail;
        this.status = status;
        this.items = List.copyOf(items);
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isPaid() {
        return status == OrderStatus.PAID;
    }

    public boolean containsProduct(String productId) {
        return items.stream().anyMatch(item -> item.productId().equals(productId));
    }

    public int totalQuantity() {
        return items.stream().mapToInt(OrderItemDocument::quantity).sum();
    }

    public String id() { return id; }
    public String userId() { return userId; }
    public String userEmail() { return userEmail; }
    public OrderStatus status() { return status; }
    public List<OrderItemDocument> items() { return items; }
    public BigDecimal totalAmount() { return totalAmount; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public enum OrderStatus {
        CREATED,
        PAID,
        CANCELLED,
        SHIPPED
    }

    public record OrderItemDocument(
            String productId,
            String productName,
            BigDecimal unitPrice,
            int quantity
    ) {
        public BigDecimal totalPrice() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
