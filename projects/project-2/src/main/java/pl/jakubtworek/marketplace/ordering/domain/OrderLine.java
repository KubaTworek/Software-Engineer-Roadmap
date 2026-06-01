package pl.jakubtworek.marketplace.ordering.domain;

import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.shared.kernel.Money;

public record OrderLine(ProductId productId, int quantity, Money unitPrice) {
    public OrderLine {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }

    public Money total() {
        return unitPrice.multiply(quantity);
    }
}
