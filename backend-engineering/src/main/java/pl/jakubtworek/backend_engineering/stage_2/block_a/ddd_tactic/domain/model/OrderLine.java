package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model;

// Entity inside the Order aggregate.
// It does not have a global repository and is modified only through Order.
public final class OrderLine implements Entity<String> {

    private final String id;
    private final ProductId productId;
    private final Quantity quantity;
    private final Money unitPrice;

    public OrderLine(
            String id,
            ProductId productId,
            Quantity quantity,
            Money unitPrice
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("OrderLine id cannot be empty");
        }

        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    @Override
    public String id() {
        return id;
    }

    public ProductId productId() {
        return productId;
    }

    public Quantity quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public Money totalPrice() {
        return unitPrice.multiply(quantity.value());
    }
}