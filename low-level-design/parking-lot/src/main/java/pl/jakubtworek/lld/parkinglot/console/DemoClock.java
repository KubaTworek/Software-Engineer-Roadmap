package pl.jakubtworek.lld.parkinglot.console;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Zegar symulacji: użytkownik przesuwa czas, zamiast czekać godzinę przy demo.
 * Nie należy do reguł parkingu — domena zależy od standardowego Clock.
 * Widoki withZone współdzielą tę samą chwilę; zmieniają interpretację strefową,
 * a nie zegar fizyczny. Klasa jest jednowątkowa, tak jak reszta laboratorium.
 */
public final class DemoClock extends Clock {

    private static final class Time {

        private Instant instant;

        private Time(Instant instant) {
            this.instant = instant;
        }
    }

    private final Time time;
    private final ZoneId zone;

    public DemoClock(Instant initial) {
        this(new Time(Objects.requireNonNull(initial, "initial")), ZoneOffset.UTC);
    }

    private DemoClock(Time time, ZoneId zone) {
        this.time = time;
        this.zone = zone;
    }

    public void advance(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Demo pozwala przesunąć czas tylko do przodu");
        }
        // Plus może zgłosić przepełnienie czasu. Obliczenie jest przed
        // przypisaniem, więc taki błąd nie zmienia bieżącej chwili symulacji.
        time.instant = time.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Instant instant() {
        return time.instant;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new DemoClock(time, Objects.requireNonNull(zone, "zone"));
    }
}
