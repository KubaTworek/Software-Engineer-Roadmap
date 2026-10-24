package pl.jakubtworek.lld.splitwise;

import java.math.BigDecimal;

/**
 * Saldo ze znakiem: plus to należność, minus zobowiązanie. Obsługujemy cały long,
 * także MIN_VALUE. Nie używamy Math.abs do interpretowania długu, bo wartość
 * bezwzględna MIN_VALUE nie mieści się w long. Kwoty pojedynczych spłat są Money.
 */
public record Balance(long grosz) {

    @Override
    public String toString() {
        return (grosz > 0 ? "+" : "") + BigDecimal.valueOf(grosz, 2).toPlainString() + " PLN";
    }
}
