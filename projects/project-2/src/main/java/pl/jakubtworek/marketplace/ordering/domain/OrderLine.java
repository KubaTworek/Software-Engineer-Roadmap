package pl.jakubtworek.marketplace.ordering.domain;

import pl.jakubtworek.marketplace.shared.kernel.Money;
import java.util.UUID;

/**
 * Ordering przechowuje obcy identyfikator jako UUID, nie jako value object Catalog.
 * Współdzielenie ProductId wygląda niewinnie, lecz sprzęga dwa modele domenowe i
 * utrudnia ich niezależną ewolucję lub wydzielenie modułu.
 */
public record OrderLine(UUID productId, int quantity, Money unitPrice) {
    public OrderLine {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }

    public Money total() {
        return unitPrice.multiply(quantity);
    }
}
