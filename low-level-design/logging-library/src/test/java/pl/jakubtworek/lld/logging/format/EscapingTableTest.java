package pl.jakubtworek.lld.logging.format;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Formatter jest tu prostą wyrocznią testową, usuniętą z gorącej ścieżki kodu.
 */
class EscapingTableTest {

    @Test
    void everyControlCharacterKeepsItsExactEscape() {
        for (int code = 0; code < 128; code++) {
            String expected = switch(code) {
                case 9 ->
                    "\\t";
                case 10 ->
                    "\\n";
                case 13 ->
                    "\\r";
                case 34 ->
                    "\\\"";
                case 92 ->
                    "\\\\";
                default ->
                    code < 32 || code == 127 ? String.format(Locale.ROOT, "\\u%04x", code) : Character.toString(code);
            };
            assertEquals(expected, Escaping.escape(Character.toString(code)), "kod " + code);
        }
    }
}
