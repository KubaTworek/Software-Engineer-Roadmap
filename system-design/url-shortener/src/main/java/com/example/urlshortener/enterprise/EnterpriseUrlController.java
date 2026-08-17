package com.example.urlshortener.enterprise;

import com.example.urlshortener.admin.AdminAuthService;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.service.ShortUrlService;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Kontroler REST odpowiedzialny za funkcje Enterprise API.
 *
 * <p>
 * Ten kontroler wystawia endpointy przeznaczone dla klientów biznesowych
 * lub integracji systemowych. W odróżnieniu od podstawowego publicznego API,
 * Enterprise API obsługuje między innymi:
 * </p>
 *
 * <ul>
 *     <li>tworzenie kluczy API dla klientów enterprise,</li>
 *     <li>hurtowe tworzenie skróconych URL-i,</li>
 *     <li>uwierzytelnianie requestów przez {@code X-Api-Key}.</li>
 * </ul>
 *
 * <p>
 * Wszystkie endpointy w tej klasie mają wspólny prefiks:
 * </p>
 *
 * <pre>
 * /api/v1/enterprise
 * </pre>
 *
 * <p>
 * Kontroler korzysta z dwóch różnych mechanizmów autoryzacji:
 * </p>
 *
 * <ul>
 *     <li>{@code X-Admin-Token} — dla operacji administracyjnych, np. tworzenia API key,</li>
 *     <li>{@code X-Api-Key} — dla klientów enterprise korzystających z bulk API.</li>
 * </ul>
 *
 * <p>
 * Klasa nie zawiera logiki generowania kluczy API ani tworzenia URL-i.
 * Deleguje te odpowiedzialności do:
 * </p>
 *
 * <ul>
 *     <li>{@link EnterpriseApiKeyService},</li>
 *     <li>{@link ShortUrlService},</li>
 *     <li>{@link AdminAuthService}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/enterprise")
public class EnterpriseUrlController {

    /**
     * Serwis odpowiedzialny za obsługę enterprise API keys.
     *
     * <p>
     * Typowe odpowiedzialności tej klasy to:
     * </p>
     *
     * <ul>
     *     <li>tworzenie nowych kluczy API,</li>
     *     <li>hashowanie kluczy przed zapisem do bazy,</li>
     *     <li>uwierzytelnianie requestów po {@code X-Api-Key},</li>
     *     <li>sprawdzanie aktywności/wygaśnięcia klucza,</li>
     *     <li>ewentualne stosowanie limitów per API key.</li>
     * </ul>
     */
    private final EnterpriseApiKeyService apiKeyService;

    /**
     * Konfiguracja funkcji enterprise.
     *
     * <p>
     * W tej klasie używana przede wszystkim do pobrania maksymalnego rozmiaru
     * requestu bulk create, czyli {@code bulkMaxSize}.
     * </p>
     */
    private final EnterpriseProperties properties;

    /**
     * Serwis domenowy do tworzenia skróconych URL-i.
     *
     * <p>
     * Endpoint bulk create deleguje do tej klasy utworzenie każdego pojedynczego
     * short URL-a, dzięki czemu zachowane są te same reguły biznesowe co w
     * zwykłym endpointcie {@code POST /api/v1/urls}.
     * </p>
     */
    private final ShortUrlService shortUrlService;

    /**
     * Serwis autoryzacji administratora.
     *
     * <p>
     * Używany do zabezpieczenia operacji administracyjnej tworzenia nowych
     * enterprise API keys.
     * </p>
     */
    private final AdminAuthService adminAuthService;

    /**
     * Konstruktor kontrolera.
     *
     * <p>
     * Spring wstrzykuje zależności przez constructor injection.
     * Dzięki temu kontroler jest łatwiejszy do testowania i nie ukrywa zależności.
     * </p>
     *
     * @param apiKeyService serwis obsługujący API keys
     * @param properties konfiguracja Enterprise API
     * @param shortUrlService serwis tworzenia skróconych URL-i
     * @param adminAuthService serwis autoryzacji administratora
     */
    public EnterpriseUrlController(
            EnterpriseApiKeyService apiKeyService,
            EnterpriseProperties properties,
            ShortUrlService shortUrlService,
            AdminAuthService adminAuthService
    ) {
        this.apiKeyService = apiKeyService;
        this.properties = properties;
        this.shortUrlService = shortUrlService;
        this.adminAuthService = adminAuthService;
    }

    /**
     * Tworzy nowy Enterprise API key.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * POST /api/v1/enterprise/api-keys
     * </pre>
     *
     * <p>
     * Wymagany nagłówek administratora:
     * </p>
     *
     * <pre>
     * X-Admin-Token: ...
     * </pre>
     *
     * <p>
     * Przykładowy request body:
     * </p>
     *
     * <pre>
     * {
     *   "clientName": "Acme Corp",
     *   "expiresAt": "2026-12-31T23:59:59Z",
     *   "requestsPerMinute": 1000
     * }
     * </pre>
     *
     * <p>
     * Przepływ:
     * </p>
     *
     * <ol>
     *     <li>Kontroler pobiera {@code X-Admin-Token} z nagłówka.</li>
     *     <li>Waliduje body przez {@link Valid}.</li>
     *     <li>Sprawdza uprawnienia administratora przez {@link AdminAuthService}.</li>
     *     <li>Deleguje utworzenie klucza do {@link EnterpriseApiKeyService}.</li>
     *     <li>Zwraca HTTP {@code 201 Created} z odpowiedzią zawierającą nowy klucz.</li>
     * </ol>
     *
     * <p>
     * Ważne: w dobrze zaprojektowanym systemie surowy API key powinien być pokazany
     * klientowi tylko raz — w odpowiedzi na utworzenie. W bazie powinien być zapisany
     * wyłącznie hash klucza, nie jego jawna wartość.
     * </p>
     *
     * @param adminToken token administratora z nagłówka {@code X-Admin-Token}
     * @param request dane potrzebne do utworzenia API key
     * @return odpowiedź z nowo utworzonym API key
     */
    @PostMapping("/api-keys")
    public ResponseEntity<CreateEnterpriseApiKeyResponse> createApiKey(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @Valid @RequestBody CreateEnterpriseApiKeyRequest request
    ) {
        /*
         * Weryfikujemy, czy request został wykonany przez administratora.
         *
         * Jeśli token jest niepoprawny albo go brakuje, metoda powinna rzucić
         * wyjątek i nie dopuścić do utworzenia API key.
         */
        adminAuthService.requireAdmin(adminToken);

        /*
         * Delegujemy tworzenie API key do serwisu enterprise.
         *
         * Serwis powinien wygenerować bezpieczny losowy klucz, zapisać jego hash
         * i zwrócić klientowi surowy klucz tylko w tej odpowiedzi.
         */
        CreateEnterpriseApiKeyResponse response = apiKeyService.create(request);

        /*
         * Zwracamy HTTP 201 Created, ponieważ powstał nowy zasób: API key.
         */
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Hurtowo tworzy skrócone URL-e dla klienta enterprise.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * POST /api/v1/enterprise/urls/bulk
     * </pre>
     *
     * <p>
     * Wymagany nagłówek:
     * </p>
     *
     * <pre>
     * X-Api-Key: ...
     * </pre>
     *
     * <p>
     * Przykładowy request body:
     * </p>
     *
     * <pre>
     * {
     *   "urls": [
     *     {
     *       "longUrl": "https://example.com/a",
     *       "customAlias": "campaign-a",
     *       "expiresAt": "2026-12-31T23:59:59Z"
     *     },
     *     {
     *       "longUrl": "https://example.com/b",
     *       "expiresAt": "2026-12-31T23:59:59Z"
     *     }
     *   ]
     * }
     * </pre>
     *
     * <p>
     * Przepływ:
     * </p>
     *
     * <ol>
     *     <li>Kontroler pobiera {@code X-Api-Key} z nagłówka.</li>
     *     <li>Waliduje request body.</li>
     *     <li>Uwierzytelnia API key przez {@link EnterpriseApiKeyService}.</li>
     *     <li>Sprawdza, czy liczba URL-i nie przekracza limitu bulk.</li>
     *     <li>Dla każdego elementu wywołuje {@link ShortUrlService#create}.</li>
     *     <li>Zwraca listę utworzonych short URL-i.</li>
     * </ol>
     *
     * <p>
     * Ta implementacja działa w trybie all-or-nothing tylko wtedy, gdy
     * {@link ShortUrlService#create} i wywołująca metoda są objęte jedną transakcją.
     * Obecnie ta metoda nie ma adnotacji {@code @Transactional}, więc każdy create
     * może wykonać się w osobnej transakcji zależnie od implementacji serwisu.
     * </p>
     *
     * @param apiKey enterprise API key z nagłówka {@code X-Api-Key}
     * @param request request zawierający listę URL-i do utworzenia
     * @return odpowiedź zawierająca liczbę żądanych i utworzonych URL-i oraz listę wyników
     */
    @PostMapping("/urls/bulk")
    public BulkCreateShortUrlResponse bulkCreate(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @Valid @RequestBody BulkCreateShortUrlRequest request
    ) {
        /*
         * Uwierzytelniamy klienta enterprise.
         *
         * Jeśli API key jest niepoprawny, wygasły albo nieaktywny, serwis powinien
         * rzucić wyjątek i zatrzymać przetwarzanie requestu.
         */
        apiKeyService.authenticate(apiKey);

        /*
         * Sprawdzamy maksymalny rozmiar requestu bulk.
         *
         * Limit chroni system przed zbyt dużymi requestami, które mogłyby:
         * - przeciążyć bazę,
         * - zająć za dużo pamięci,
         * - zbyt długo blokować wątek requestu,
         * - spowodować timeout po stronie klienta lub gatewaya.
         */
        if (request.urls().size() > properties.getBulkMaxSize()) {
            throw new IllegalArgumentException(
                    "Bulk create limit exceeded. Max: " + properties.getBulkMaxSize()
            );
        }

        /*
         * Tworzymy short URL dla każdego elementu requestu.
         *
         * Użycie stream().map(shortUrlService::create) oznacza, że dla każdego
         * CreateShortUrlRequest zostanie wywołana standardowa logika tworzenia URL-a.
         *
         * Dzięki temu bulk endpoint zachowuje te same zasady co publiczne API:
         * - walidacja longUrl,
         * - custom alias,
         * - reserved aliases,
         * - expiration,
         * - zapis do bazy,
         * - generowanie short code.
         */
        List<CreateShortUrlResponse> created = request.urls().stream()
                .map(shortUrlService::create)
                .toList();

        /*
         * Zwracamy podsumowanie operacji.
         *
         * requestedCount = liczba URL-i w request body
         * createdCount   = liczba faktycznie utworzonych URL-i
         * created        = lista odpowiedzi dla każdego utworzonego URL-a
         */
        return new BulkCreateShortUrlResponse(
                request.urls().size(),
                created.size(),
                created
        );
    }
}