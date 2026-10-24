package pl.jakubtworek.lld.logging.domain;

import java.util.*;

/**
 * Czysta transformacja kontekstu przed zdarzeniem i formatowaniem.
 * Nie wyszukuje sekretów w wiadomości ani komunikacie wyjątku sinka.
 * Custom implementacja ma zwracać nie-null LogContext, bez rekurencyjnego logowania.
 */
@FunctionalInterface
public interface ContextMasker {

    LogContext mask(LogContext context);

    static ContextMasker identity() {
        return context -> context;
    }

    static ContextMasker redact(Set<String> keys, String replacement) {
        var protectedKeys = Set.copyOf(keys);
        Objects.requireNonNull(replacement);
        // Te same reguły kluczy co w kontekście, także dla nieobecnych pól.
        var validation = new HashMap<String, String>();
        protectedKeys.forEach(key -> validation.put(key, ""));
        new LogContext(validation);
        return context -> {
            var masked = new TreeMap<>(context.fields());
            protectedKeys.forEach(key -> {
                if (masked.containsKey(key))
                    masked.put(key, replacement);
            });
            return new LogContext(masked);
        };
    }
}
