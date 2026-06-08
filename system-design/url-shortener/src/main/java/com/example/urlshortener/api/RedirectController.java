package com.example.urlshortener.api;

import com.example.urlshortener.analytics.ClickTrackingService;
import com.example.urlshortener.edge.EdgeProperties;
import com.example.urlshortener.service.ShortUrlService;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kontroler REST odpowiedzialny za publiczne przekierowania krótkich linków.
 *
 * <p>
 * Jest to jeden z najważniejszych komponentów całego URL shortenera, ponieważ
 * obsługuje wejścia użytkowników w skrócone linki, np.:
 * </p>
 *
 * <pre>
 * GET /aB92xK7
 * GET /promo-2026
 * </pre>
 *
 * <p>
 * Kontroler:
 * </p>
 *
 * <ol>
 *     <li>pobiera {@code shortCode} ze ścieżki URL,</li>
 *     <li>rozwiązuje go na oryginalny {@code longUrl},</li>
 *     <li>publikuje event kliknięcia do analytics,</li>
 *     <li>ustawia nagłówek {@code Location},</li>
 *     <li>ustawia nagłówek {@code Cache-Control} dla CDN/edge,</li>
 *     <li>zwraca odpowiedź HTTP {@code 302 Found}.</li>
 * </ol>
 *
 * <p>
 * Klasa nie zawiera logiki odczytu z bazy ani cache. Te odpowiedzialności są
 * delegowane do {@link ShortUrlService}. Dzięki temu kontroler pozostaje cienką
 * warstwą HTTP.
 * </p>
 *
 * <p>
 * Ważne: endpoint redirectu jest ścieżką o wysokim ruchu. Powinien być szybki,
 * prosty i odporny na awarie komponentów pomocniczych, takich jak analytics.
 * </p>
 */
@RestController
public class RedirectController {

    /**
     * Serwis odpowiedzialny za rozwiązywanie short code do long URL.
     *
     * <p>
     * Typowo {@link ShortUrlService#resolveLongUrl(String)} wykonuje:
     * </p>
     *
     * <ul>
     *     <li>odczyt z cache, np. Redis,</li>
     *     <li>fallback do bazy danych, jeśli cache nie zawiera wpisu,</li>
     *     <li>walidację statusu linku, np. ACTIVE/BLOCKED/EXPIRED,</li>
     *     <li>sprawdzenie daty wygaśnięcia,</li>
     *     <li>zwrócenie docelowego long URL.</li>
     * </ul>
     */
    private final ShortUrlService shortUrlService;

    /**
     * Serwis odpowiedzialny za rejestrowanie kliknięcia.
     *
     * <p>
     * Nie powinien synchronicznie zapisywać eventu do bazy w ścieżce redirectu.
     * W dobrze zaprojektowanej wersji publikuje event do kolejki, np. RabbitMQ,
     * Kafka albo Pub/Sub, a dalsze przetwarzanie analytics odbywa się asynchronicznie.
     * </p>
     */
    private final ClickTrackingService clickTrackingService;

    /**
     * Konfiguracja zachowania edge/CDN.
     *
     * <p>
     * W tej klasie używana do pobrania wartości nagłówka:
     * </p>
     *
     * <pre>
     * Cache-Control
     * </pre>
     *
     * <p>
     * dla odpowiedzi redirectu.
     * </p>
     */
    private final EdgeProperties edgeProperties;

    /**
     * Konstruktor kontrolera.
     *
     * <p>
     * Spring wstrzykuje wszystkie zależności przez constructor injection.
     * Dzięki temu zależności są jawne, a klasa jest łatwiejsza do testowania.
     * </p>
     *
     * @param shortUrlService serwis rozwiązywania short URL
     * @param clickTrackingService serwis publikowania eventów kliknięć
     * @param edgeProperties konfiguracja cache/CDN/edge
     */
    public RedirectController(
            ShortUrlService shortUrlService,
            ClickTrackingService clickTrackingService,
            EdgeProperties edgeProperties
    ) {
        this.shortUrlService = shortUrlService;
        this.clickTrackingService = clickTrackingService;
        this.edgeProperties = edgeProperties;
    }

    /**
     * Obsługuje publiczny redirect dla short code.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * GET /{shortCode}
     * </pre>
     *
     * <p>
     * Przykłady:
     * </p>
     *
     * <pre>
     * GET /aB92xK7
     * GET /promo-2026
     * GET /my_alias
     * </pre>
     *
     * <p>
     * Wyrażenie regularne w mapowaniu:
     * </p>
     *
     * <pre>
     * {shortCode:[A-Za-z0-9_-]+}
     * </pre>
     *
     * <p>
     * oznacza, że short code może zawierać:
     * </p>
     *
     * <ul>
     *     <li>litery małe i wielkie,</li>
     *     <li>cyfry,</li>
     *     <li>podkreślnik,</li>
     *     <li>myślnik.</li>
     * </ul>
     *
     * <p>
     * Jeśli short code nie istnieje, wygasł albo został zablokowany,
     * {@link ShortUrlService} powinien rzucić odpowiedni wyjątek, który zostanie
     * obsłużony przez globalny handler błędów.
     * </p>
     *
     * @param shortCode kod skróconego linku pobrany ze ścieżki URL
     * @param request request HTTP, używany do zebrania danych analytics
     * @return odpowiedź HTTP 302 z nagłówkiem Location
     */
    @GetMapping("/{shortCode:[A-Za-z0-9_-]+}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request
    ) {

        /*
         * Rozwiązujemy shortCode do docelowego longUrl.
         *
         * Ta operacja powinna być możliwie szybka, najlepiej:
         *
         * Redis/cache -> fallback DB
         *
         * Jeśli link jest nieaktywny, wygasły lub zablokowany, serwis powinien
         * rzucić wyjątek zamiast zwracać longUrl.
         */
        String longUrl = shortUrlService.resolveLongUrl(shortCode);

        /*
         * Rejestrujemy kliknięcie.
         *
         * W tej implementacji clickTrackingService publikuje event do kolejki.
         * Event może zawierać m.in.:
         *
         * - shortCode,
         * - timestamp,
         * - IP klienta,
         * - User-Agent,
         * - Referer,
         * - kraj z CDN,
         * - request id.
         *
         * Uwaga projektowa:
         * jeśli publisher kolejki może rzucić wyjątek, warto rozważyć obsłużenie
         * go tak, aby awaria analytics nie zepsuła redirectu.
         */
        clickTrackingService.track(shortCode, request);

        /*
         * Tworzymy nagłówki odpowiedzi HTTP.
         */
        HttpHeaders headers = new HttpHeaders();

        /*
         * Ustawiamy nagłówek Location.
         *
         * To właśnie ten nagłówek mówi przeglądarce, gdzie ma przekierować
         * użytkownika.
         *
         * Przykład odpowiedzi:
         *
         * HTTP/1.1 302 Found
         * Location: https://example.com/landing-page
         */
        headers.setLocation(URI.create(longUrl));

        /*
         * Ustawiamy Cache-Control dla redirectów.
         *
         * Wartość pochodzi z EdgeProperties, np.:
         *
         * public, max-age=60, s-maxage=300
         *
         * albo:
         *
         * no-store
         *
         * Zależnie od konfiguracji można pozwolić CDN/edge cache'ować redirecty
         * albo całkowicie wyłączyć ich cache'owanie.
         */
        headers.set(HttpHeaders.CACHE_CONTROL, edgeProperties.getCacheControlForRedirects());

        /*
         * Zwracamy odpowiedź 302 Found.
         *
         * 302 jest bezpiecznym domyślnym wyborem dla URL shortenera, ponieważ
         * nie sugeruje klientom i przeglądarkom, że przekierowanie jest permanentne.
         *
         * Dzięki temu system nadal ma kontrolę nad linkiem:
         * - może go zablokować,
         * - może go wygasić,
         * - może zmienić zachowanie w przyszłości,
         * - może nadal zbierać analytics.
         */
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}