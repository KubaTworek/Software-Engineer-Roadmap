package com.example.autocomplete.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizuje tekst używany w autocomplete.
 *
 * Ta klasa jest krytyczna, bo ten sam tekst może występować w wielu wariantach:
 *
 * - "iPhone 15"
 * - "IPHONE-15"
 * - " iphone   15 "
 * - "iPhone!!! 15"
 *
 * Po normalizacji wszystkie powinny być możliwie blisko jednej postaci:
 *
 * - "iphone 15"
 *
 * Normalizacja jest używana m.in. przez:
 * - indeksowanie sugestii,
 * - wyszukiwanie po query,
 * - budowanie cache key,
 * - deduplikację,
 * - safety filtering,
 * - ranking prefix match.
 */
@Component
public class TextNormalizer {

    /**
     * Regex wykrywający znaki diakrytyczne po rozbiciu Unicode.
     *
     * Przykład:
     * "é" po Normalizer.Form.NFD może zostać rozbite na:
     * - "e"
     * - znak akcentu
     *
     * Ten pattern usuwa sam znak akcentu.
     */
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    /**
     * Regex znaków niedozwolonych w reprezentacji searchowej.
     *
     * Zostawiamy tylko:
     * - małe litery a-z,
     * - cyfry 0-9,
     * - białe znaki.
     *
     * Wszystko inne, np. "-", "!", ".", "_", zamieniamy później na spację.
     */
    private static final Pattern NON_SEARCH_CHARS = Pattern.compile("[^a-z0-9\\s]");

    /**
     * Regex wykrywający wiele spacji lub białych znaków obok siebie.
     *
     * Pozwala zamienić np.:
     * "iphone     15     pro"
     *
     * na:
     * "iphone 15 pro"
     */
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    /**
     * Zwraca znormalizowaną wersję tekstu.
     *
     * Główne kroki:
     * 1. null -> pusty string,
     * 2. lowercase,
     * 3. ręczna transliteracja znaków, których Java Unicode NFD nie obsługuje idealnie,
     * 4. Unicode normalization,
     * 5. usunięcie znaków diakrytycznych,
     * 6. zamiana znaków specjalnych na spacje,
     * 7. redukcja wielu spacji,
     * 8. trim.
     */
    public String normalize(String input) {

        /*
         * Bezpieczny fallback.
         *
         * Dzięki temu reszta pipeline'u nie musi osobno sprawdzać nulli.
         */
        if (input == null) {
            return "";
        }

        /*
         * Locale.ROOT daje stabilne lowercase niezależne od języka systemu.
         *
         * To ważne, bo np. tureckie "I" potrafi zachowywać się inaczej
         * przy locale-specific lowercasing.
         */
        String s = input.toLowerCase(Locale.ROOT);

        /*
         * Ręczna zamiana wybranych znaków narodowych/specjalnych.
         *
         * Niektóre znaki, np. polskie "ł", nie zawsze są poprawnie sprowadzane
         * do ASCII przez standardowe usuwanie diakrytyków.
         *
         * Przykład:
         * "Łódź" -> "lodz"
         */
        s = s.replace("ą", "a")
                .replace("ć", "c")
                .replace("ę", "e")
                .replace("ł", "l")
                .replace("ń", "n")
                .replace("ó", "o")
                .replace("ś", "s")
                .replace("ź", "z")
                .replace("ż", "z")
                .replace("ß", "ss")
                .replace("æ", "ae")
                .replace("œ", "oe")
                .replace("ø", "o")
                .replace("đ", "d");

        /*
         * Rozbijamy znaki Unicode na bazowy znak + diakrytyki.
         *
         * Przykład:
         * "é" -> "e" + akcent
         */
        s = Normalizer.normalize(s, Normalizer.Form.NFD);

        /*
         * Usuwamy znaki diakrytyczne po Unicode decomposition.
         *
         * Przykład:
         * "café" -> "cafe"
         */
        s = DIACRITICS.matcher(s).replaceAll("");

        /*
         * Usuwamy znaki, które nie są przydatne w wyszukiwaniu prefiksowym.
         *
         * Zamieniamy je na spację zamiast usuwać, żeby nie sklejać przypadkowo słów.
         *
         * Przykład:
         * "iphone-15" -> "iphone 15"
         */
        s = NON_SEARCH_CHARS.matcher(s).replaceAll(" ");

        /*
         * Redukujemy wiele białych znaków do jednej spacji.
         *
         * Przykład:
         * "iphone     15" -> "iphone 15"
         */
        s = MULTIPLE_SPACES.matcher(s).replaceAll(" ");

        /*
         * Usuwamy spacje z początku i końca.
         */
        return s.trim();
    }
}