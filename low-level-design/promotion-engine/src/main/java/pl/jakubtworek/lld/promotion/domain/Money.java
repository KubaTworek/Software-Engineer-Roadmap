package pl.jakubtworek.lld.promotion.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Nieujemna kwota w jednej, jawnie ustalonej walucie PLN. Long przechowuje
 * grosze, więc 0.10 + 0.20 daje dokładnie 0.30. To nie jest ogólna biblioteka
 * walut: dodanie EUR wymagałoby uwzględnienia waluty w równości i operacjach.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money {
        if (cents < 0)
            throw new IllegalArgumentException("Kwota nie może być ujemna");
    }

    public static Money pln(String text) {
        if (text == null || !text.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Kwota PLN: np. 12.30, najwyżej dwa miejsca po kropce");
        }
        return new Money(new BigDecimal(text).movePointRight(2).longValueExact());
    }

    // Przepełnienie jest błędem danych/obliczenia. Nigdy nie zawijamy ceny do minusa.
    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(cents, other.cents));
    }

    public Money times(int quantity) {
        if (quantity < 0)
            throw new IllegalArgumentException("Ujemny mnożnik kwoty");
        return new Money(Math.multiplyExact(cents, quantity));
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, Objects.requireNonNull(other).cents);
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(cents, 2).toPlainString() + " PLN";
    }
}
