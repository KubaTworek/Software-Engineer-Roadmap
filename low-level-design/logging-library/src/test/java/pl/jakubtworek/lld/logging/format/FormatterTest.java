package pl.jakubtworek.lld.logging.format;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.lld.logging.domain.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Format ma zachowywać treść przez jawne kodowanie, a nie usuwać znaki po cichu.
 * Sprawdzamy dokładny schemat i ASCII control; nie jest to ogólny parser JSON.
 */
class FormatterTest {

    static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    @Test
    void plainAndJsonHaveDeterministicShapeAndUnicode() {
        var event = new LogEvent(NOW, LogLevel.WARN, "order", "Zażółć gęślą");
        assertEquals("2026-10-23T12:00:00Z WARN [order] Zażółć gęślą", new PlainTextFormatter().format(event));
        assertEquals("{\"timestamp\":\"2026-10-23T12:00:00Z\",\"level\":\"WARN\",\"logger\":\"order\",\"message\":\"Zażółć gęślą\"}", new JsonLogFormatter().format(event));
    }

    @Test
    void quotesBackslashesAndNewlinesAreEscapedInNameAndMessage() {
        var event = new LogEvent(NOW, LogLevel.INFO, "a\"\n", "line1\r\nline2\t\\\"");
        String json = new JsonLogFormatter().format(event);
        assertTrue(json.contains("\"logger\":\"a\\\"\\n\""));
        assertTrue(json.contains("\"message\":\"line1\\r\\nline2\\t\\\\\\\"\""));
        for (LogFormatter formatter : java.util.List.of(new PlainTextFormatter(), new JsonLogFormatter())) {
            String text = formatter.format(event);
            assertFalse(text.contains("\n"));
            assertFalse(text.contains("\r"));
            assertFalse(text.contains("\t"));
        }
    }

    @Test
    void allAsciiControlCharactersAreEncodedRatherThanInserted() {
        for (int code = 0; code < 32; code++) {
            var event = new LogEvent(NOW, LogLevel.INFO, "logger", "before" + (char) code + "after");
            for (LogFormatter formatter : java.util.List.of(new PlainTextFormatter(), new JsonLogFormatter())) {
                String result = formatter.format(event);
                assertFalse(result.chars().anyMatch(c -> c < 32));
                assertTrue(result.contains("before"));
                assertTrue(result.contains("after"));
            }
        }
    }
}
