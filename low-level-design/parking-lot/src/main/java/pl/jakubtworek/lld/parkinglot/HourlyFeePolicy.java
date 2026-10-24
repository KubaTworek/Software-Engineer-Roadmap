package pl.jakubtworek.lld.parkinglot;

import java.time.Duration;
import java.util.Objects;

/**
 * Cennik za każdą rozpoczętą godzinę PO odjęciu okresu bezpłatnego. Dla 10 min
 * gratis: 10 min kosztuje zero, 10 min + 1 ns jedną stawkę, 70 min jedną,
 * 70 min + 1 ns dwie. To precyzyjny kontrakt, a nie nieokreślone „za godzinę”.
 * Liczymy czas trwania między Instant, więc zmiana strefy lub DST nie dopisuje
 * godziny do rzeczywiście przebytego czasu. Kwoty pozostają w groszach.
 */
public final class HourlyFeePolicy implements FeePolicy {

    private final Money hourlyRate;
    private final Duration freePeriod;

    public HourlyFeePolicy(Money hourlyRate, Duration freePeriod) {
        this.hourlyRate = Objects.requireNonNull(hourlyRate, "hourlyRate");
        this.freePeriod = Objects.requireNonNull(freePeriod, "freePeriod");
        if (freePeriod.isNegative()) {
            throw new IllegalArgumentException("Okres bezpłatny nie może być ujemny");
        }
    }

    @Override
    public Money calculate(PricingContext context) {
        // Chronologię zapewnia konstruktor kontekstu. Nie duplikujemy jej
        // tutaj i w FlatFeePolicy; każdy bazowy cennik dostaje tę samą gwarancję.
        Duration stay = Objects.requireNonNull(context, "context").duration();
        if (stay.compareTo(freePeriod) <= 0) {
            return new Money(0);
        }
        Duration billable = stay.minus(freePeriod);
        long hours = billable.getSeconds() / 3600;
        // Nie używamy toHours() bez reszty: skróciłoby rozpoczętą godzinę.
        // Nie używamy toNanos(): duży czas może przepełnić long. Sekundy
        // i nanos-of-second pozwalają zaokrąglić nawet minimalny ułamek w górę.
        if (billable.getSeconds() % 3600 != 0 || billable.getNano() != 0) {
            hours++;
        }
        return hourlyRate.times(hours);
    }
}
