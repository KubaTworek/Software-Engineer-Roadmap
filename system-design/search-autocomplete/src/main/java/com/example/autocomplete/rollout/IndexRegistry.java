package com.example.autocomplete.rollout;

import com.example.autocomplete.index.AutocompleteIndex;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Centralny rejestr indeksów autocomplete dostępnych w aplikacji.
 *
 * Ta klasa odpowiada za:
 * - przechowywanie wielu wersji indeksu,
 * - wskazywanie aktualnie aktywnego indeksu,
 * - aktywację nowej wersji indeksu,
 * - rollback do poprzedniej wersji.
 *
 * Dzięki temu AutocompleteService nie musi wiedzieć, jak zarządzane są wersje.
 * Pyta tylko registry.active() i zawsze dostaje indeks, którego ma aktualnie używać.
 */
@Component
public class IndexRegistry {

    /**
     * Mapa wszystkich załadowanych indeksów.
     *
     * Klucz: wersja indeksu, np.:
     * - index-v1
     * - index-v2-canary
     * - batch-1720000000000
     *
     * Wartość: gotowy indeks autocomplete.
     *
     * LinkedHashMap zachowuje kolejność dodawania, co ułatwia debugowanie
     * i czytelne zwracanie listy wersji.
     */
    private final Map<String, AutocompleteIndex> indexes = new LinkedHashMap<>();

    /**
     * Wersja indeksu, która aktualnie obsługuje ruch.
     *
     * AutocompleteService używa właśnie tej wersji przez registry.active().
     */
    private String activeVersion;

    /**
     * Poprzednio aktywna wersja indeksu.
     *
     * Jest potrzebna do rollbacku.
     * Po aktywacji nowego indeksu zapamiętujemy starą wersję tutaj.
     */
    private String previousVersion;

    /**
     * Rejestruje nową wersję indeksu w pamięci aplikacji.
     *
     * Samo register() nie przełącza ruchu, jeśli aktywny indeks już istnieje.
     * Wyjątek: pierwszy zarejestrowany indeks automatycznie staje się aktywny.
     *
     * To pozwala najpierw załadować nowy indeks, a dopiero potem świadomie
     * przełączyć ruch przez activate().
     */
    public synchronized void register(AutocompleteIndex index) {
        indexes.put(index.version(), index);

        /*
         * Pierwszy indeks musi zostać ustawiony jako aktywny,
         * inaczej aplikacja nie miałaby czym obsługiwać requestów autocomplete.
         */
        if (activeVersion == null) {
            activeVersion = index.version();
        }
    }

    /**
     * Przełącza ruch na wskazaną wersję indeksu.
     *
     * Przykład:
     * activate("index-v2-canary")
     *
     * Mechanizm:
     * - sprawdzamy, czy indeks o takiej wersji istnieje,
     * - zapamiętujemy obecną aktywną wersję jako previousVersion,
     * - ustawiamy nową activeVersion.
     *
     * Od tego momentu AutocompleteService zacznie korzystać z nowego indeksu.
     */
    public synchronized void activate(String version) {
        if (!indexes.containsKey(version)) {
            throw new IllegalArgumentException("Unknown index version: " + version);
        }

        previousVersion = activeVersion;
        activeVersion = version;
    }

    /**
     * Przywraca poprzednią aktywną wersję indeksu.
     *
     * To jest szybki mechanizm awaryjny, gdy nowy indeks:
     * - ma błędne dane,
     * - pogarsza jakość sugestii,
     * - powoduje wzrost latency,
     * - zawiera niechciane sugestie.
     *
     * Rollback nie odbudowuje indeksu.
     * Po prostu przełącza wskaźnik activeVersion na poprzednią wersję.
     */
    public synchronized void rollback() {
        if (previousVersion == null) {
            throw new IllegalStateException("No previous index version available");
        }

        /*
         * Zapamiętujemy wersję, do której chcemy wrócić.
         */
        String rollbackTarget = previousVersion;

        /*
         * Aktualna wersja staje się previousVersion,
         * dzięki czemu można wykonać ponowny rollback/przełączenie w drugą stronę.
         */
        previousVersion = activeVersion;

        /*
         * Ruch wraca na poprzedni indeks.
         */
        activeVersion = rollbackTarget;
    }

    /**
     * Zwraca aktualnie aktywny indeks.
     *
     * To jest najważniejsza metoda z punktu widzenia runtime'u.
     * AutocompleteService wywołuje ją przy obsłudze requestu, żeby wiedzieć,
     * z którego indeksu pobierać kandydatów.
     */
    public synchronized AutocompleteIndex active() {
        AutocompleteIndex index = indexes.get(activeVersion);

        /*
         * Taka sytuacja nie powinna wystąpić po poprawnym starcie aplikacji.
         * Jeśli wystąpi, oznacza błąd inicjalizacji albo niepoprawne zarządzanie registry.
         */
        if (index == null) {
            throw new IllegalStateException("No active index");
        }

        return index;
    }

    /**
     * Zwraca listę wszystkich załadowanych wersji indeksu.
     *
     * Używane przez endpoint diagnostyczny/adminowy:
     * GET /autocomplete/index/versions
     */
    public synchronized List<String> versions() {
        return List.copyOf(indexes.keySet());
    }

    /**
     * Zwraca nazwę aktualnie aktywnej wersji indeksu.
     *
     * Przydatne do:
     * - debugowania,
     * - nagłówków odpowiedzi API,
     * - metryk,
     * - logów,
     * - potwierdzenia rollbacku.
     */
    public synchronized String activeVersion() {
        return activeVersion;
    }
}