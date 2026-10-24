package pl.jakubtworek.lld.splitwise;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Nieujemna kwota w groszach PLN. Ujemne saldo jest innym pojęciem (Balance),
 * więc nie rozluźniamy walidacji Money, żeby zapisać dług. Zero może być udziałem
 * przy podziale bardzo małej kwoty, ale nie samym wydatkiem lub spłatą.
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

    @Override
    public String toString() {
        return BigDecimal.valueOf(grosz, 2).toPlainString() + " PLN";
    }
}
