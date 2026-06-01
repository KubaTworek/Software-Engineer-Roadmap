package pl.jakubtworek.marketplace.inventory.domain;

import pl.jakubtworek.marketplace.shared.kernel.AggregateRoot;

import java.util.UUID;

public class StockItem extends AggregateRoot {
    private final UUID productId;
    private int availableQuantity;
    private int reservedQuantity;

    private StockItem(UUID productId, int availableQuantity, int reservedQuantity) {
        if (availableQuantity < 0 || reservedQuantity < 0) throw new IllegalArgumentException("quantity cannot be negative");
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
    }

    public static StockItem create(UUID productId, int quantity) {
        return new StockItem(productId, quantity, 0);
    }

    public void add(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        availableQuantity += quantity;
    }

    public boolean canReserve(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        return availableQuantity >= quantity;
    }

    public void reserveWithoutPublishingEvent(int quantity) {
        if (!canReserve(quantity)) throw new IllegalStateException("not enough stock");
        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    /**
     * Kept for aggregate-level unit tests. The application handler uses one order-level StockReserved event instead.
     */
    public boolean reserve(UUID orderId, int quantity, UUID correlationId, UUID causationId) {
        if (!canReserve(quantity)) {
            registerEvent(StockReservationFailed.now(orderId, productId, "Not enough stock", correlationId, causationId));
            return false;
        }
        reserveWithoutPublishingEvent(quantity);
        registerEvent(StockReserved.now(orderId, java.util.List.of(new StockReserved.Line(productId, quantity)), correlationId, causationId));
        return true;
    }

    public UUID productId() { return productId; }
    public int availableQuantity() { return availableQuantity; }
    public int reservedQuantity() { return reservedQuantity; }
}
