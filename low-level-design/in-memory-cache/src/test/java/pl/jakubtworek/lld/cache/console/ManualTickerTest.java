package pl.jakubtworek.lld.cache.console;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Granice czasu sprawdzamy bez zależności od szybkości wykonywania testu.
 */
class ManualTickerTest {

    @Test
    void advanceRejectsNegativeAndOverflowWithoutPartialChange() {
        var ticker = new ManualTicker();
        ticker.advance(Duration.ofNanos(Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> ticker.advance(Duration.ofNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> ticker.advance(Duration.ofNanos(-1)));
        assertEquals(Long.MAX_VALUE, ticker.read());
    }

    @Test
    void durationConversionOverflowDoesNotMoveTick() {
        var ticker = new ManualTicker();
        assertThrows(ArithmeticException.class, () -> ticker.advance(Duration.ofSeconds(Long.MAX_VALUE)));
        ticker.advance(Duration.ofNanos(3));
        assertEquals(3, ticker.read());
    }
}
