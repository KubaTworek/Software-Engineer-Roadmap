package pl.jakubtworek.lld.cache.console;

import pl.jakubtworek.lld.cache.domain.Ticker;
import java.time.Duration;

/**
 * Monotoniczny zegar symulacji niezależny od daty. Sprawdzenie zakresu
 * poprzedza przypisanie, więc błąd advance nie przesuwa częściowo czasu.
 */
final class ManualTicker implements Ticker {

    private long nanos;

    public long read() {
        return nanos;
    }

    void advance(Duration duration) {
        if (duration.isNegative())
            throw new IllegalArgumentException("Nie cofamy czasu w demo");
        nanos = Math.addExact(nanos, duration.toNanos());
    }
}
