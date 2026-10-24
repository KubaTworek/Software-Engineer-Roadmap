package pl.jakubtworek.lld.parkinglot;

import java.math.BigDecimal;

/**
 * Nieujemna kwota w groszach, zawsze w PLN. To świadomie jeden rodzaj waluty:
 * 501 oznacza dokładnie 5.01 PLN, bez błędu reprezentacji zmiennoprzecinkowej.
 * Przepełnienie jest odmową obliczenia, a nie zawinięciem do ujemnej opłaty.
 * Dla wielu walut trzeba rozszerzyć kontrakt, zamiast dopisywać dowolny napis.
 */
public record Money(long grosz) {

    public Money {
        if (grosz < 0) {
            throw new IllegalArgumentException("Kwota nie może być ujemna");
        }
    }

    public Money times(long quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Mnożnik nie może być ujemny");
        }
        return new Money(Math.multiplyExact(grosz, quantity));
    }

    @Override
    public String toString() {
        // BigDecimal służy tu tylko do dokładnego formatowania. Reguły opłat
        // liczymy w long; nie konwertujemy przez double nawet na potrzeby demo.
        return BigDecimal.valueOf(grosz, 2).toPlainString() + " PLN";
    }
}
