package pl.jakubtworek.lld.vendingmachine;

import java.math.BigDecimal;

/**
 * Nieujemna wartość w groszach PLN, niezależna od konkretnych monet.
 * Money(100) opisuje kwotę, ale nie dowodzi, że automat ma monetę 1 PLN.
 * Cel edukacyjny: odróżnić wartość pieniężną od fizycznej reprezentacji.
 * Obliczenia są dokładne, a przepełnienie jest błędem przed publikacją stanu.
 */
public record Money(long grosz) {

    public Money {
        if (grosz < 0) {
            throw new IllegalArgumentException("Kwota nie może być ujemna");
        }
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(grosz, other.grosz));
    }

    public Money minus(Money other) {
        if (grosz < other.grosz) {
            throw new IllegalArgumentException("Brak środków do odjęcia");
        }
        return new Money(grosz - other.grosz);
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(grosz, 2).toPlainString() + " PLN";
    }
}
