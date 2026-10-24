package pl.jakubtworek.lld.atm;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Nieujemne grosze PLN. Konto może mieć niepełne złotówki, ale bankomat wypłaca tylko dozwolone wielokrotności.
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

    public Money minus(Money other) {
        return new Money(Math.subtractExact(grosz, Objects.requireNonNull(other, "other").grosz));
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(grosz, 2).toPlainString() + " PLN";
    }
}
