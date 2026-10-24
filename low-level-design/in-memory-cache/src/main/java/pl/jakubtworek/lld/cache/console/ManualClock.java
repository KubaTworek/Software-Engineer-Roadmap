package pl.jakubtworek.lld.cache.console;

import java.time.*;
import java.util.Objects;

/**
 * Zegar adaptera do jawnego przesuwania symulacji, bez Thread.sleep.
 * Zmiana strefy tworzy widok tego samego czasu; Instant nie zależy od strefy.
 * Nie jest zegarem domenowym cache ani narzędziem synchronizacji wątków.
 */
final class ManualClock extends Clock {

    private static final class Time {

        Instant now;

        Time(Instant now) {
            this.now = now;
        }
    }

    private final Time time;
    private final ZoneId zone;

    ManualClock(Instant now) {
        this(new Time(Objects.requireNonNull(now)), ZoneOffset.UTC);
    }

    private ManualClock(Time time, ZoneId zone) {
        this.time = time;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new ManualClock(time, Objects.requireNonNull(zone));
    }

    @Override
    public Instant instant() {
        return time.now;
    }

    void advance(Duration duration) {
        if (duration.isNegative())
            throw new IllegalArgumentException("Nie cofamy czasu w demo");
        // Wyjątek zakresu nie publikuje zmiany czasu.
        time.now = time.now.plus(duration);
    }

    /**
     * Świadoma korekta daty, również wstecz. Nie ma wpływu na Ticker ani TTL.
     */
    void shift(Duration duration) {
        time.now = time.now.plus(duration);
    }
}
