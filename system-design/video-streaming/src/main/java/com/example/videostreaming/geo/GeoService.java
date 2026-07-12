package com.example.videostreaming.geo;

import com.example.videostreaming.catalog.Video;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serwis odpowiedzialny za geo-blocking.
 *
 * Główna odpowiedzialność:
 * - rozpoznać kraj użytkownika na podstawie nagłówków HTTP,
 * - sprawdzić, czy dany film jest dostępny w tym kraju.
 *
 * Używany m.in. przez:
 * - PlaybackController przed wydaniem URL-i do odtwarzania,
 * - DrmLicenseController przed wydaniem licencji DRM.
 *
 * Ważne:
 * Ten serwis zakłada, że nagłówki kraju są ustawiane przez zaufaną warstwę,
 * np. CDN, reverse proxy albo API Gateway.
 * Nie powinno się ufać takim nagłówkom, jeśli klient może je ustawić samodzielnie.
 */
@Service
public class GeoService {

    /**
     * Rozpoznaje kraj użytkownika z nagłówków requestu.
     *
     * Kolejność sprawdzania:
     * 1. X-Geo-Country — własny nagłówek aplikacji/proxy.
     * 2. CloudFront-Viewer-Country — nagłówek z AWS CloudFront.
     * 3. CF-IPCountry — nagłówek z Cloudflare.
     *
     * Zwracany format:
     * - kod kraju uppercase, np. PL, DE, US,
     * - null, jeśli kraju nie udało się ustalić.
     *
     * Dlaczego uppercase:
     * ujednolicamy format, żeby porównanie kraju było odporne
     * na różnice typu "pl", "Pl", "PL".
     */
    public String resolveCountry(HttpServletRequest request) {
        String explicit = request.getHeader("X-Geo-Country");

        if (explicit == null || explicit.isBlank()) {
            explicit = request.getHeader("CloudFront-Viewer-Country");
        }

        if (explicit == null || explicit.isBlank()) {
            explicit = request.getHeader("CF-IPCountry");
        }

        if (explicit == null || explicit.isBlank()) {
            return null;
        }

        return explicit.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Sprawdza, czy film jest dostępny w kraju użytkownika.
     *
     * Reguły:
     * - jeśli film nie ma ustawionej listy allowedCountries, jest dostępny globalnie,
     * - jeśli lista allowedCountries istnieje, kraj użytkownika musi być znany,
     * - kraj użytkownika musi znajdować się na liście dozwolonych krajów.
     *
     * allowedCountries jest trzymane jako CSV, np.:
     * "PL,DE,US".
     *
     * Przykład:
     * allowedCountries = "PL,DE"
     * country = "PL"
     * wynik = true
     *
     * allowedCountries = "PL,DE"
     * country = "US"
     * wynik = false
     *
     * allowedCountries = "PL,DE"
     * country = null
     * wynik = false
     */
    public boolean isAllowed(Video video, String country) {
        String allowedCountries = video.getAllowedCountries();

        /*
         * Brak ograniczeń regionalnych oznacza dostęp globalny.
         */
        if (allowedCountries == null || allowedCountries.isBlank()) {
            return true;
        }

        /*
         * Jeśli film ma ograniczenia regionalne, a nie znamy kraju użytkownika,
         * odmawiamy dostępu.
         *
         * To bezpieczniejsze niż wpuszczanie użytkownika bez potwierdzonej lokalizacji.
         */
        if (country == null || country.isBlank()) {
            return false;
        }

        /*
         * Parsujemy listę krajów z CSV do Set.
         *
         * Trim usuwa przypadkowe spacje,
         * filter usuwa puste wpisy,
         * uppercase normalizuje kody krajów.
         */
        Set<String> allowed = Arrays.stream(allowedCountries.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return allowed.contains(country.toUpperCase(Locale.ROOT));
    }
}