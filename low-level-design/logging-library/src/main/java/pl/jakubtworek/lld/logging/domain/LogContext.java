package pl.jakubtworek.lld.logging.domain;

import java.util.*;

/**
 * Jawne pola jednego wywołania, bez ThreadLocal i globalnego MDC.
 * Kopia sortowana po kluczu daje deterministyczny format. Klucze to proste
 * identyfikatory ASCII; wartości są nie-null String, także puste.
 * Zagnieżdżony obiekt context w JSON nie koliduje z polami zdarzenia.
 */
public record LogContext(Map<String, String> fields) {

    public LogContext {
        var copy = new TreeMap<String, String>();
        fields.forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
                throw new IllegalArgumentException("Niepoprawny klucz kontekstu");
            }
            copy.put(key, Objects.requireNonNull(value));
        });
        fields = Collections.unmodifiableSortedMap(copy);
    }

    public static LogContext empty() {
        return new LogContext(Map.of());
    }

    public LogContext with(String key, String value) {
        var copy = new TreeMap<>(fields);
        copy.put(key, value);
        return new LogContext(copy);
    }
}
