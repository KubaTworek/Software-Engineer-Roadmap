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

    public boolean reserve(UUID orderId, int quantity, UUID correlationId, UUID causationId) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (availableQuantity < quantity) {
            registerEvent(StockReservationFailed.now(productId, orderId, "Not enough stock", correlationId, causationId));
            return false;
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
        registerEvent(StockReserved.now(productId, orderId, correlationId, causationId));
        return true;
    }

    public UUID productId() { return productId; }
    public int availableQuantity() { return availableQuantity; }
    public int reservedQuantity() { return reservedQuantity; }
}
