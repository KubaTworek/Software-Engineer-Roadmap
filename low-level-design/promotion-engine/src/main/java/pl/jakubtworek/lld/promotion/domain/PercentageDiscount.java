package pl.jakubtworek.lld.promotion.domain;

import java.util.Objects;

/**
 * Procent dotyczy kwalifikowanej podstawy po poprzednich alokacjach, z HALF_UP.
 * Dwa razy 10% z 100 PLN oznacza 10 PLN i 9 PLN rabatu. Dodatni procent może
 * zaokrąglić się do zera; taka promocja nie blokuje kolejnych promocji.
 */
public record PercentageDiscount(Percent percent) implements Discount {

    public PercentageDiscount {
        Objects.requireNonNull(percent);
        if (percent.basisPoints() == 0)
            throw new IllegalArgumentException("Rabat procentowy musi być dodatni");
    }

    @Override
    public Money calculate(Money remaining) {
        return percent.of(remaining);
    }
}
