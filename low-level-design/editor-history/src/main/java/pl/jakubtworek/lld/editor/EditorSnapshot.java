package pl.jakubtworek.lld.editor;

import java.util.Objects;

/**
 * Stabilna para numeru wersji i korzenia rope. Odczyt snapshotu nie kopiuje tekstu.
 * Rozgałęzienia nawigacyjne są zapytaniem EditorHistory; nie dodajemy do dawnego
 * widoku mutowalnej listy potomków, która zmieniałaby się przy późniejszych edycjach.
 */
public record EditorSnapshot(long version, RopeText text) {

    public EditorSnapshot {
        if (version < 0) {
            throw new IllegalArgumentException("Ujemna wersja");
        }
        Objects.requireNonNull(text);
    }
}
