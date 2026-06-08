package com.example.urlshortener.api;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.service.ShortUrlService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Kontroler REST odpowiedzialny za publiczne API zarządzania skróconymi URL-ami.
 *
 * <p>
 * Ten kontroler obsługuje operacje związane z tworzeniem krótkiego linku
 * oraz pobieraniem szczegółów istniejącego short code.
 * </p>
 *
 * <p>
 * Wszystkie endpointy w tej klasie mają wspólny prefiks:
 * </p>
 *
 * <pre>
 * /api/v1/urls
 * </pre>
 *
 * <p>
 * Dostępne endpointy:
 * </p>
 *
 * <ul>
 *     <li>{@code POST /api/v1/urls} — tworzy nowy skrócony URL,</li>
 *     <li>{@code GET /api/v1/urls/{shortCode}} — zwraca szczegóły konkretnego short code.</li>
 * </ul>
 *
 * <p>
 * Klasa jest oznaczona jako {@link RestController}, więc Spring traktuje ją jako
 * kontroler REST. Zwracane obiekty DTO są automatycznie serializowane do JSON-a.
 * </p>
 *
 * <p>
 * Kontroler nie zawiera logiki biznesowej. Nie generuje short code, nie waliduje
 * domeny ręcznie, nie zapisuje encji bezpośrednio do repozytorium. Wszystko to
 * deleguje do {@link ShortUrlService}.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/urls")
public class ShortUrlApiController {

    /**
     * Serwis aplikacyjny odpowiedzialny za operacje na skróconych URL-ach.
     *
     * <p>
     * To w {@link ShortUrlService} znajduje się właściwa logika biznesowa, np.:
     * </p>
     *
     * <ul>
     *     <li>walidacja długiego URL-a,</li>
     *     <li>walidacja custom aliasu,</li>
     *     <li>generowanie short code,</li>
     *     <li>zapis do bazy danych,</li>
     *     <li>odczyt szczegółów URL-a,</li>
     *     <li>obsługa statusów typu ACTIVE, BLOCKED, EXPIRED.</li>
     * </ul>
     */
    private final ShortUrlService shortUrlService;

    /**
     * Konstruktor kontrolera.
     *
     * <p>
     * Spring wstrzykuje {@link ShortUrlService} przez constructor injection.
     * Jest to preferowany sposób wstrzykiwania zależności, ponieważ:
     * </p>
     *
     * <ul>
     *     <li>zależności są jawne,</li>
     *     <li>łatwiej pisać testy jednostkowe,</li>
     *     <li>pole może być oznaczone jako {@code final},</li>
     *     <li>obiekt nie może powstać bez wymaganych zależności.</li>
     * </ul>
     *
     * @param shortUrlService serwis obsługujący logikę skróconych URL-i
     */
    public ShortUrlApiController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    /**
     * Tworzy nowy skrócony URL.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * POST /api/v1/urls
     * </pre>
     *
     * <p>
     * Przykładowy request:
     * </p>
     *
     * <pre>
     * {
     *   "longUrl": "https://example.com/very/long/path",
     *   "customAlias": "promo-2026",
     *   "expiresAt": "2026-12-31T23:59:59Z"
     * }
     * </pre>
     *
     * <p>
     * Pola requestu zależą od klasy {@link CreateShortUrlRequest}. Typowo zawiera ona:
     * </p>
     *
     * <ul>
     *     <li>{@code longUrl} — docelowy adres URL,</li>
     *     <li>{@code customAlias} — opcjonalny alias użytkownika,</li>
     *     <li>{@code expiresAt} — opcjonalna data wygaśnięcia linku.</li>
     * </ul>
     *
     * <p>
     * Adnotacja {@link Valid} powoduje uruchomienie walidacji Bean Validation
     * na obiekcie requestu. Jeśli request nie spełnia reguł walidacji, np.
     * brakuje wymaganego pola albo data wygaśnięcia jest z przeszłości,
     * kontroler nie wywoła metody serwisowej. Spring zwróci błąd walidacji.
     * </p>
     *
     * <p>
     * Metoda zwraca status HTTP {@code 201 Created}, ponieważ jej efektem jest
     * utworzenie nowego zasobu.
     * </p>
     *
     * @param request dane potrzebne do utworzenia skróconego URL-a
     * @return odpowiedź zawierająca utworzony short code i short URL
     */
    @PostMapping
    public ResponseEntity<CreateShortUrlResponse> create(
            @Valid @RequestBody CreateShortUrlRequest request
    ) {
        /*
         * Delegujemy właściwą logikę tworzenia linku do ShortUrlService.
         *
         * Serwis powinien:
         * - zwalidować longUrl,
         * - sprawdzić custom alias,
         * - wygenerować shortCode, jeśli alias nie został podany,
         * - zapisać rekord w bazie,
         * - przygotować DTO odpowiedzi.
         */
        CreateShortUrlResponse response = shortUrlService.create(request);

        /*
         * Zwracamy HTTP 201 Created z body odpowiedzi.
         */
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Pobiera szczegóły istniejącego skróconego URL-a.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * GET /api/v1/urls/{shortCode}
     * </pre>
     *
     * <p>
     * Przykład:
     * </p>
     *
     * <pre>
     * GET /api/v1/urls/aB92xK7
     * </pre>
     *
     * <p>
     * Typowa odpowiedź może zawierać:
     * </p>
     *
     * <ul>
     *     <li>ID rekordu,</li>
     *     <li>short code,</li>
     *     <li>pełny short URL,</li>
     *     <li>docelowy long URL,</li>
     *     <li>status linku,</li>
     *     <li>datę utworzenia,</li>
     *     <li>datę wygaśnięcia,</li>
     *     <li>informacje o blokadzie, jeśli występują.</li>
     * </ul>
     *
     * <p>
     * Jeśli short code nie istnieje, {@link ShortUrlService#getDetails(String)}
     * powinien rzucić wyjątek domenowy, np. {@code ShortUrlNotFoundException}.
     * Globalny handler błędów powinien zamienić go na HTTP {@code 404 Not Found}.
     * </p>
     *
     * @param shortCode kod skróconego linku pobrany ze ścieżki URL
     * @return szczegóły skróconego URL-a
     */
    @GetMapping("/{shortCode}")
    public UrlDetailsResponse details(@PathVariable String shortCode) {
        /*
         * Delegujemy odczyt szczegółów do warstwy serwisowej.
         *
         * Kontroler nie powinien samodzielnie odpytywać repozytorium,
         * bo reguły domenowe i mapowanie na DTO powinny być w serwisie.
         */
        return shortUrlService.getDetails(shortCode);
    }
}