package pl.jakubtworek.lld.cache.console;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Zegar symulacji ma współdzielić Instant między strefami i odmawiać błędnego
 * przesunięcia bez zmiany czasu; nie testujemy upływu czasu rzeczywistego.
 */
class ManualClockTest {

    @Test
    void zoneViewSharesTimeButNotZone() {
        var start = Instant.parse("2026-10-23T12:00:00Z");
        var clock = new ManualClock(start);
        var zoned = clock.withZone(ZoneId.of("Europe/Warsaw"));
        clock.advance(Duration.ofSeconds(2));
        assertEquals(start.plusSeconds(2), zoned.instant());
        assertEquals(ZoneOffset.UTC, clock.getZone());
        assertEquals(ZoneId.of("Europe/Warsaw"), zoned.getZone());
    }

    @Test
    void invalidAdvancePreservesInstant() {
        var clock = new ManualClock(Instant.MAX);
        assertThrows(DateTimeException.class, () -> clock.advance(Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> clock.advance(Duration.ofNanos(-1)));
        assertEquals(Instant.MAX, clock.instant());
    }
}
