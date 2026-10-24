package pl.jakubtworek.lld.promotion.domain;

import java.util.Objects;

/**
 * Kwota nominalna może przewyższać saldo; ograniczenie należy do wspólnej polityki silnika.
 */
public record FixedDiscount(Money amount) implements Discount {

    public FixedDiscount {
        Objects.requireNonNull(amount);
        if (amount.cents() == 0)
            throw new IllegalArgumentException("Rabat kwotowy musi być dodatni");
    }

    @Override
    public Money calculate(Money remaining) {
        Objects.requireNonNull(remaining);
        return amount;
    }
}
