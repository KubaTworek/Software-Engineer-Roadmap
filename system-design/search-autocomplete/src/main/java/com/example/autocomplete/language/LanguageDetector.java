package com.example.autocomplete.language;

import org.springframework.stereotype.Component;

/**
 * Prosty detector języka/locale dla requestu autocomplete.
 *
 * Locale wpływa później na:
 * - ranking sugestii,
 * - filtrowanie sugestii po kraju/języku,
 * - lokalne trendy,
 * - wybór indeksu lub wariantów danych,
 * - cache key.
 *
 * To nie jest pełny language detection model.
 * To lekka heurystyka dobra na potrzeby tego etapu projektu.
 */
@Component
public class LanguageDetector {

    /**
     * Wykrywa locale requestu.
     *
     * Priorytet:
     * 1. jeśli klient podał requestedLocale, używamy go,
     * 2. jeśli query zawiera polskie znaki, zwracamy pl-PL,
     * 3. w przeciwnym razie fallback to en-US.
     */
    public String detect(String query, String requestedLocale) {

        /*
         * Najpierw ufamy locale podanemu przez klienta.
         *
         * Przykład:
         * /autocomplete?q=iphone&locale=pl-PL
         *
         * To ważne, bo samo query często nie wystarcza do wykrycia języka.
         * Fraza "iphone" wygląda tak samo po polsku, angielsku i wielu innych językach.
         */
        if (requestedLocale != null && !requestedLocale.isBlank()) {
            return requestedLocale;
        }

        /*
         * Prosta heurystyka dla języka polskiego.
         *
         * Jeśli query zawiera polskie znaki diakrytyczne,
         * zakładamy locale pl-PL.
         *
         * Przykład:
         * "ładowarka"
         * "książka"
         * "słuchawki"
         */
        if (query != null && query.matches(".*[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ].*")) {
            return "pl-PL";
        }

        /*
         * Domyślny fallback.
         *
         * Jeśli nie mamy żadnego mocniejszego sygnału,
         * traktujemy request jako en-US.
         *
         * W produkcji fallback mógłby zależeć od:
         * - kraju,
         * - ustawień użytkownika,
         * - nagłówka Accept-Language,
         * - konfiguracji tenant/workspace.
         */
        return "en-US";
    }
}