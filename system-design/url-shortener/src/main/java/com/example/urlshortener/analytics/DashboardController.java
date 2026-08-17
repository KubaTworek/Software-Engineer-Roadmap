package com.example.urlshortener.analytics;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kontroler REST odpowiedzialny za udostępnianie danych dashboardu analitycznego.
 *
 * <p>
 * Ten kontroler wystawia endpointy HTTP pozwalające pobrać:
 * </p>
 *
 * <ul>
 *     <li>globalne podsumowanie systemu,</li>
 *     <li>szczegółową analitykę konkretnego short code.</li>
 * </ul>
 *
 * <p>
 * Wszystkie endpointy w tej klasie mają wspólny prefiks:
 * </p>
 *
 * <pre>
 * /api/v1/dashboard
 * </pre>
 *
 * <p>
 * Klasa jest oznaczona jako {@link RestController}, więc Spring traktuje ją
 * jako kontroler REST. Zwracane obiekty Java są automatycznie serializowane
 * do JSON-a.
 * </p>
 *
 * <p>
 * Kontroler nie zawiera logiki biznesowej ani zapytań do repozytoriów.
 * Jego zadaniem jest:
 * </p>
 *
 * <ul>
 *     <li>przyjęcie requestu HTTP,</li>
 *     <li>odczytanie parametrów ścieżki i query string,</li>
 *     <li>delegacja do {@link DashboardService},</li>
 *     <li>zwrócenie odpowiedzi DTO.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    /**
     * Serwis aplikacyjny odpowiedzialny za przygotowanie danych dashboardu.
     *
     * <p>
     * To w {@link DashboardService} powinna znajdować się logika agregowania
     * danych, odczytu z repozytoriów, Redis counterów i ewentualnych fallbacków.
     * </p>
     *
     * <p>
     * Dzięki temu kontroler pozostaje cienką warstwą HTTP.
     * </p>
     */
    private final DashboardService dashboardService;

    /**
     * Konstruktor kontrolera.
     *
     * <p>
     * Spring wstrzykuje {@link DashboardService} przez constructor injection.
     * Jest to preferowane podejście, ponieważ zależność jest jawna i łatwa
     * do zamockowania w testach.
     * </p>
     *
     * @param dashboardService serwis przygotowujący dane dashboardu
     */
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Zwraca globalne podsumowanie dashboardu.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * GET /api/v1/dashboard/summary
     * </pre>
     *
     * <p>
     * Przykładowe zastosowanie:
     * </p>
     *
     * <pre>
     * curl http://localhost:8080/api/v1/dashboard/summary
     * </pre>
     *
     * <p>
     * Typowo odpowiedź może zawierać takie dane jak:
     * </p>
     *
     * <ul>
     *     <li>łączna liczba utworzonych URL-i,</li>
     *     <li>liczba aktywnych URL-i,</li>
     *     <li>liczba zablokowanych URL-i,</li>
     *     <li>łączna liczba kliknięć,</li>
     *     <li>liczba podejrzanych kliknięć,</li>
     *     <li>inne metryki systemowe lub analityczne.</li>
     * </ul>
     *
     * <p>
     * Szczegółowy kształt odpowiedzi zależy od klasy
     * {@link DashboardSummaryResponse}.
     * </p>
     *
     * @return DTO z globalnym podsumowaniem dashboardu
     */
    @GetMapping("/summary")
    public DashboardSummaryResponse summary() {

        /*
         * Delegujemy przygotowanie danych do DashboardService.
         *
         * Kontroler nie powinien samodzielnie wykonywać zapytań do bazy,
         * Redis ani innych źródeł danych.
         */
        return dashboardService.summary();
    }

    /**
     * Zwraca analitykę dla konkretnego short code.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * GET /api/v1/dashboard/urls/{shortCode}/analytics
     * </pre>
     *
     * <p>
     * Przykłady:
     * </p>
     *
     * <pre>
     * GET /api/v1/dashboard/urls/aB92xK7/analytics
     * GET /api/v1/dashboard/urls/aB92xK7/analytics?from=2026-06-01&amp;to=2026-06-07
     * </pre>
     *
     * <p>
     * Parametry:
     * </p>
     *
     * <ul>
     *     <li>{@code shortCode} — kod skróconego linku ze ścieżki URL,</li>
     *     <li>{@code from} — opcjonalna data początkowa zakresu,</li>
     *     <li>{@code to} — opcjonalna data końcowa zakresu.</li>
     * </ul>
     *
     * <p>
     * Parametry {@code from} i {@code to} są opcjonalne. Jeśli ich nie podano,
     * {@link DashboardService} powinien przyjąć domyślny zakres, np. ostatnie
     * 7 lub 30 dni — zależnie od implementacji.
     * </p>
     *
     * <p>
     * Adnotacja:
     * </p>
     *
     * <pre>
     * @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
     * </pre>
     *
     * <p>
     * mówi Springowi, że parametr query string ma być parsowany jako data
     * w formacie ISO, np.:
     * </p>
     *
     * <pre>
     * 2026-06-07
     * </pre>
     *
     * <p>
     * Jeśli klient poda niepoprawny format daty, Spring zwróci błąd walidacji
     * lub błąd typu {@code 400 Bad Request}, zależnie od konfiguracji obsługi błędów.
     * </p>
     *
     * @param shortCode kod skróconego linku, dla którego pobieramy analytics
     * @param from opcjonalna data początkowa zakresu w formacie ISO, np. {@code 2026-06-01}
     * @param to opcjonalna data końcowa zakresu w formacie ISO, np. {@code 2026-06-07}
     * @return DTO z analityką konkretnego short code
     */
    @GetMapping("/urls/{shortCode}/analytics")
    public UrlAnalyticsResponse analytics(
            @PathVariable String shortCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {

        /*
         * Delegujemy całą logikę analityczną do DashboardService.
         *
         * To serwis powinien:
         * - zwalidować zakres dat,
         * - ustawić wartości domyślne,
         * - pobrać dane z repozytoriów,
         * - odczytać szybkie liczniki z Redisa,
         * - przygotować odpowiedź UrlAnalyticsResponse.
         */
        return dashboardService.analyticsFor(shortCode, from, to);
    }
}