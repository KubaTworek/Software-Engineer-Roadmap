package pl.jakubtworek.lld.carrental;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Nieujemne grosze PLN. Przepełnienie jest błędem przed publikacją, nie zawinięciem kwoty.
 */
public record Money(long grosz) {

    public Money {
        if (grosz < 0) {
            throw new IllegalArgumentException("Kwota nie może być ujemna");
        }
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(grosz, Objects.requireNonNull(other, "other").grosz));
    }

    public Money times(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Mnożnik nie może być ujemny");
        }
        return new Money(Math.multiplyExact(grosz, count));
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(grosz, 2).toPlainString() + " PLN";
    }
}
