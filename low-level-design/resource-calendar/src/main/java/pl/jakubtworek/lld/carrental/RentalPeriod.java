package pl.jakubtworek.lld.carrental;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Półotwarty przedział [start,end): auto wraca w dniu end, który może być początkiem
 * następnego wynajmu. Testowanie tylko początku drugiego przedziału nie wykryje
 * pełnego zawierania; warunek dwóch ścisłych nierówności obejmuje oba kierunki.
 * Dni są kalendarzowe: nie przeliczamy zmiany czasu letniego na godziny ani doby UTC.
 */
public record RentalPeriod(LocalDate start, LocalDate end) {

    public RentalPeriod {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Początek musi poprzedzać koniec");
        }
    }

    public long days() {
        return ChronoUnit.DAYS.between(start, end);
    }

    public boolean overlaps(RentalPeriod other) {
        Objects.requireNonNull(other, "other");
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
