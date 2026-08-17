package com.example.urlshortener.validation;

import com.example.urlshortener.exception.ReservedAliasException;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Komponent odpowiedzialny za walidację aliasów użytkownika pod kątem nazw zarezerwowanych.
 *
 * <p>
 * Alias to własny, czytelny short code wybrany przez użytkownika, np.:
 * </p>
 *
 * <pre>
 * https://sho.rt/promo2026
 * https://sho.rt/black-friday
 * https://sho.rt/my-campaign
 * </pre>
 *
 * <p>
 * Problem polega na tym, że niektóre ścieżki w aplikacji są już używane
 * przez endpointy systemowe albo pliki techniczne. Użytkownik nie powinien
 * móc zarejestrować aliasu takiego jak {@code api}, {@code admin}
 * czy {@code actuator}, ponieważ prowadziłoby to do konfliktu tras HTTP.
 * </p>
 *
 * <p>
 * Przykład konfliktu:
 * </p>
 *
 * <pre>
 * /api/v1/urls
 * /admin
 * /health
 * /metrics
 * </pre>
 *
 * <p>
 * Jeśli użytkownik mógłby utworzyć short URL o aliasie {@code api},
 * wtedy adres:
 * </p>
 *
 * <pre>
 * https://sho.rt/api
 * </pre>
 *
 * <p>
 * mógłby kolidować semantycznie albo technicznie z endpointami aplikacji.
 * </p>
 *
 * <p>
 * Ta klasa pilnuje, aby alias użytkownika nie był jedną z nazw zarezerwowanych.
 * Nie odpowiada natomiast za pełną walidację formatu aliasu, np. długość,
 * dozwolone znaki albo lowercase. To powinno być obsłużone osobno, np.
 * w DTO, serwisie albo dodatkowym walidatorze.
 * </p>
 */
@Component
public class AliasValidator {

    /**
     * Zbiór aliasów zarezerwowanych przez system.
     *
     * <p>
     * Alias znajdujący się na tej liście nie może zostać użyty przez użytkownika
     * jako custom alias.
     * </p>
     *
     * <p>
     * Lista zawiera między innymi:
     * </p>
     *
     * <ul>
     *     <li>{@code api} — prefiks endpointów REST API,</li>
     *     <li>{@code admin} — potencjalny prefiks panelu administracyjnego,</li>
     *     <li>{@code login}, {@code logout}, {@code signup} — typowe ścieżki auth,</li>
     *     <li>{@code health}, {@code metrics}, {@code actuator} — endpointy operacyjne,</li>
     *     <li>{@code robots.txt}, {@code favicon.ico} — standardowe pliki webowe.</li>
     * </ul>
     *
     * <p>
     * Użycie {@link Set} jest celowe, ponieważ sprawdzanie obecności elementu
     * przez {@code contains()} jest szybkie i czytelne.
     * </p>
     *
     * <p>
     * Zbiór tworzony przez {@code Set.of(...)} jest niemodyfikowalny.
     * Oznacza to, że po uruchomieniu aplikacji nie da się przypadkowo dodać
     * lub usunąć aliasu z tej listy przez referencję do kolekcji.
     * </p>
     */
    private static final Set<String> RESERVED_ALIASES = Set.of(
            "api",
            "admin",
            "login",
            "logout",
            "signup",
            "health",
            "metrics",
            "actuator",
            "robots.txt",
            "favicon.ico"
    );

    /**
     * Sprawdza, czy podany alias nie jest nazwą zarezerwowaną.
     *
     * <p>
     * Jeśli alias jest zarezerwowany, metoda rzuca {@link ReservedAliasException}.
     * Taki wyjątek powinien zostać później obsłużony przez globalny handler błędów
     * i zamieniony na odpowiedź HTTP, np. {@code 400 Bad Request} albo
     * {@code 409 Conflict}, zależnie od przyjętej konwencji API.
     * </p>
     *
     * <p>
     * Jeśli alias jest {@code null}, metoda nic nie robi.
     * Jest to przydatne, ponieważ custom alias może być opcjonalny. Gdy użytkownik
     * nie poda aliasu, system może wygenerować short code automatycznie.
     * </p>
     *
     * <p>
     * Obecna implementacja sprawdza alias dokładnie takim stringiem, jaki został
     * przekazany do metody. Oznacza to, że sprawdzenie jest case-sensitive.
     * </p>
     *
     * <p>
     * Przykład:
     * </p>
     *
     * <pre>
     * validateNotReserved("api")   -> rzuci ReservedAliasException
     * validateNotReserved("admin") -> rzuci ReservedAliasException
     * validateNotReserved("Api")   -> nie rzuci wyjątku w obecnej wersji
     * validateNotReserved(null)    -> nic nie zrobi
     * </pre>
     *
     * @param alias alias podany przez użytkownika; może być {@code null}
     * @throws ReservedAliasException jeśli alias znajduje się na liście nazw zarezerwowanych
     */
    public void validateNotReserved(String alias) {
        /*
         * Jeśli alias jest nullem, nie ma czego walidować.
         *
         * Taki przypadek oznacza zwykle, że użytkownik nie podał custom aliasu
         * i aplikacja wygeneruje short code automatycznie.
         */
        if (alias != null && RESERVED_ALIASES.contains(alias)) {
            /*
             * Alias jest zarezerwowany przez system.
             *
             * Rzucamy wyjątek domenowy, aby wyższa warstwa aplikacji mogła
             * zwrócić użytkownikowi czytelny błąd.
             */
            throw new ReservedAliasException(alias);
        }
    }
}