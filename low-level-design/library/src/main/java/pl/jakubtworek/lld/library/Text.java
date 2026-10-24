package pl.jakubtworek.lld.library;

import java.util.Locale;
import java.util.Objects;

/**
 * Wspólna walidacja wartości tekstowych wewnątrz tego ćwiczenia. Identyfikatory
 * są kodami katalogowymi, nie ISBN. Normalizacja ROOT nie zależy od języka
 * komputera. Pozostawiamy osobne typy ID, choć mają taką samą składnię.
 */
final class Text {

    private Text() {
    }

    static String nonBlank(String value) {
        String normalized = Objects.requireNonNull(value, "value").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Tekst nie może być pusty");
        }
        return normalized;
    }

    static String id(String value) {
        String normalized = nonBlank(value).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9-]{1,32}")) {
            throw new IllegalArgumentException("Kod wymaga 1..32 znaków A-Z, cyfr lub myślnika");
        }
        return normalized;
    }
}
