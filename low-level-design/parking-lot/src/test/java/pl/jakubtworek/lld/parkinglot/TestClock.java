package pl.jakubtworek.lld.parkinglot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kontrolowane źródło czasu testu, także do celowego cofnięcia zegara.
 * Nie używamy sleep. Widoki innych stref współdzielą chwilę; AtomicReference
 * jest tu uchwytem do wspólnej wartości, nie dowodem współbieżności parkingu.
 */
final class TestClock extends Clock {

    private final AtomicReference<Instant> time;
    private final AtomicBoolean failing;
    private final ZoneId zone;

    TestClock() {
        this(new AtomicReference<>(Instant.parse("2026-01-01T10:00:00Z")), new AtomicBoolean(), ZoneOffset.UTC);
    }

    private TestClock(AtomicReference<Instant> time, AtomicBoolean failing, ZoneId zone) {
        this.time = time;
        this.failing = failing;
        this.zone = zone;
    }

    // Awaria źródła to inny scenariusz niż poprawny odczyt starszej daty.
    // Tryb awarii jest wspólny dla widoków strefowych tego zegara.
    void failReads(boolean fail) {
        failing.set(fail);
    }

    void advance(Duration duration) {
        time.updateAndGet(now -> now.plus(duration));
    }

    void set(Instant instant) {
        time.set(instant);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Instant instant() {
        if (failing.get()) {
            throw new IllegalStateException("Awaria źródła czasu");
        }
        return time.get();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new TestClock(time, failing, zone);
    }
}
