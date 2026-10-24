package pl.jakubtworek.lld.promotion.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Punkt bazowy to 0.01 punktu procentowego: 1250 oznacza 12.50%, a 10000 = 100%.
 * Zakres 0..100% chroni kontrakt rabatu. BigInteger jest tu tylko pośrednim
 * iloczynem: poprawny procent Long.MAX_VALUE nie może przepełnić mnożenia long.
 */
public record Percent(int basisPoints) {

    public Percent {
        if (basisPoints < 0 || basisPoints > 10000)
            throw new IllegalArgumentException("Procent musi należeć do 0..100");
    }

    public static Percent of(String text) {
        if (text == null || !text.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Procent: np. 12.50, najwyżej dwa miejsca po kropce");
        }
        return new Percent(new BigDecimal(text).movePointRight(2).intValueExact());
    }

    public Money of(Money base) {
        Objects.requireNonNull(base);
        // Dla nieujemnych kwot HALF_UP to dodanie połowy dzielnika i dzielenie
        // całkowite. Zaokrąglamy kwotę rabatu raz, a nie każdą sztukę osobno.
        BigInteger numerator = BigInteger.valueOf(base.cents()).multiply(BigInteger.valueOf(basisPoints));
        return new Money(numerator.add(BigInteger.valueOf(5000)).divide(BigInteger.valueOf(10000)).longValueExact());
    }
}
