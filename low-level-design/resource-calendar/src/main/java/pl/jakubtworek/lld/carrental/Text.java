package pl.jakubtworek.lld.carrental;

import java.util.Locale;
import java.util.Objects;

/**
 * Wartości normalizujemy niezależnie od ustawień języka komputera. Kody nie są VIN.
 */
final class Text {

    private Text() {
    }

    static String nonBlank(String value) {
        String result = Objects.requireNonNull(value, "value").strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Tekst nie może być pusty");
        }
        return result;
    }

    static String id(String value) {
        String result = nonBlank(value).toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z0-9-]{1,32}")) {
            throw new IllegalArgumentException("Kod wymaga 1..32 znaków A-Z, cyfr lub myślnika");
        }
        return result;
    }
}
