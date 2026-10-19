package pl.jakubtworek.booking.education.refactoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Granica wydzielona metodą branch by abstraction z kodu legacy.
 *
 * <p>Wersja legacy pobierała datę statycznie i wykonywała obliczenia na double.
 * Wydzielenie {@link Clock} pozwala najpierw zabezpieczyć zachowanie testem
 * characterization, a dopiero potem zmieniać algorytm. To ważna kolejność:
 * „czystszy” kod bez testu może przypadkiem zmienić regułę zaokrąglenia albo
 * granicę promocji i nadal wyglądać przekonująco w code review.</p>
 */
public final class ReservationFeePolicy {

    private final Clock clock;

    public ReservationFeePolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BigDecimal fee(BigDecimal basePrice, LocalDate eventDate) {
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(eventDate, "eventDate");
        if (basePrice.signum() < 0) {
            throw new IllegalArgumentException("basePrice cannot be negative");
        }

        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        // ChronoUnit liczy całkowitą odległość. Period#getDays zwraca tylko
        // składową dni po odjęciu pełnych miesięcy i byłby subtelnym błędem.
        long days = ChronoUnit.DAYS.between(today, eventDate);
        BigDecimal rate = days >= 30 ? new BigDecimal("0.02") : new BigDecimal("0.05");
        return basePrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
