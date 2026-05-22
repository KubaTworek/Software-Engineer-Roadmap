package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model;

// Entity inside the Order aggregate.
// It is not loaded or saved independently through its own repository.
public final class OrderLine {

    private final ProductId productId;
    private final int quantity;
    private final Money unitPrice;

    public OrderLine(ProductId productId, int quantity, Money unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Money total() {
        return unitPrice.multiply(quantity);
    }

    public ProductId productId() {
        return productId;
    }

    public int quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }
}