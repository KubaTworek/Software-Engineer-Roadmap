package com.example.urlshortener.edge;

import com.example.urlshortener.exception.AdminUnauthorizedException;
import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.region.RegionProperties;
import com.example.urlshortener.storage.DistributedUrlStore;
import com.example.urlshortener.storage.UrlLookupRecord;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wewnętrzny kontroler REST używany przez warstwę edge/CDN do szybkiego lookupu short code.
 *
 * <p>
 * Ten kontroler nie jest przeznaczony dla zwykłych użytkowników końcowych.
 * Powinien być wywoływany przez zaufaną warstwę edge, np.:
 * </p>
 *
 * <ul>
 *     <li>Cloudflare Worker,</li>
 *     <li>Fastly Compute,</li>
 *     <li>Lambda@Edge,</li>
 *     <li>wewnętrzny gateway CDN,</li>
 *     <li>inny komponent działający przed origin backendem.</li>
 * </ul>
 *
 * <p>
 * Publiczny użytkownik zwykle wywołuje:
 * </p>
 *
 * <pre>
 * GET /{shortCode}
 * </pre>
 *
 * <p>
 * Natomiast edge worker może wywołać origin:
 * </p>
 *
 * <pre>
 * GET /internal/edge/urls/{shortCode}
 * X-Edge-Token: ...
 * </pre>
 *
 * <p>
 * Odpowiedź tego endpointu zawiera dane potrzebne edge'owi do wykonania redirectu:
 * </p>
 *
 * <ul>
 *     <li>short code,</li>
 *     <li>docelowy long URL,</li>
 *     <li>status linku,</li>
 *     <li>datę wygaśnięcia,</li>
 *     <li>region pochodzenia danych,</li>
 *     <li>sugerowany TTL cache,</li>
 *     <li>informację, czy link jest redirectable.</li>
 * </ul>
 *
 * <p>
 * Dzięki temu warstwa edge może cache'ować lookup i wykonywać redirect bliżej
 * użytkownika, bez każdorazowego odpytywania głównego backendu.
 * </p>
 */
@RestController
@RequestMapping("/internal/edge")
public class EdgeLookupController {

    /**
     * Abstrakcja odczytu danych URL z rozproszonego storage.
     *
     * <p>
     * W implementacji lokalnej może to być PostgreSQL/JPA. W architekturze globalnej
     * może to być adapter do:
     * </p>
     *
     * <ul>
     *     <li>DynamoDB Global Tables,</li>
     *     <li>Cassandra/ScyllaDB,</li>
     *     <li>CockroachDB,</li>
     *     <li>Spanner,</li>
     *     <li>innego globalnie replikowanego key-value store.</li>
     * </ul>
     *
     * <p>
     * Kontroler nie powinien znać szczegółów konkretnej bazy. Interesuje go tylko
     * lookup po {@code shortCode}.
     * </p>
     */
    private final DistributedUrlStore distributedUrlStore;

    /**
     * Konfiguracja funkcji edge.
     *
     * <p>
     * Używana tutaj głównie do:
     * </p>
     *
     * <ul>
     *     <li>sprawdzenia, czy edge lookup jest włączony,</li>
     *     <li>pobrania wewnętrznego tokenu używanego przez edge/CDN.</li>
     * </ul>
     */
    private final EdgeProperties edgeProperties;

    /**
     * Konfiguracja regionu i TTL-i cache.
     *
     * <p>
     * W tej klasie używana do pobrania:
     * </p>
     *
     * <ul>
     *     <li>TTL dla pozytywnych odpowiedzi edge cache,</li>
     *     <li>TTL dla negatywnych odpowiedzi edge cache.</li>
     * </ul>
     *
     * <p>
     * Pozytywna odpowiedź oznacza, że short code istnieje i można go przekierować.
     * Negatywna odpowiedź oznacza np. link wygasły, zablokowany albo nieaktywny.
     * </p>
     */
    private final RegionProperties regionProperties;

    /**
     * Zegar aplikacji.
     *
     * <p>
     * Używany do sprawdzenia, czy link wygasł.
     * Wstrzyknięcie {@link Clock} zamiast używania {@code Instant.now()} bezpośrednio
     * ułatwia testowanie tej klasy.
     * </p>
     */
    private final Clock clock;

    /**
     * Konstruktor kontrolera.
     *
     * <p>
     * Spring wstrzykuje wszystkie zależności przez constructor injection.
     * </p>
     *
     * @param distributedUrlStore store używany do lookupu short code
     * @param edgeProperties konfiguracja edge/CDN
     * @param regionProperties konfiguracja regionu i TTL-i
     * @param clock zegar aplikacji
     */
    public EdgeLookupController(
            DistributedUrlStore distributedUrlStore,
            EdgeProperties edgeProperties,
            RegionProperties regionProperties,
            Clock clock
    ) {
        this.distributedUrlStore = distributedUrlStore;
        this.edgeProperties = edgeProperties;
        this.regionProperties = regionProperties;
        this.clock = clock;
    }

    /**
     * Wykonuje lookup danych short URL dla warstwy edge/CDN.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * GET /internal/edge/urls/{shortCode}
     * </pre>
     *
     * <p>
     * Wymagany nagłówek:
     * </p>
     *
     * <pre>
     * X-Edge-Token: ...
     * </pre>
     *
     * <p>
     * Przepływ działania:
     * </p>
     *
     * <ol>
     *     <li>Weryfikuje token edge.</li>
     *     <li>Odczytuje rekord URL z {@link DistributedUrlStore}.</li>
     *     <li>Jeśli short code nie istnieje, rzuca {@link ShortUrlNotFoundException}.</li>
     *     <li>Sprawdza, czy rekord jest aktywny i niewygasły.</li>
     *     <li>Dobiera TTL dla cache.</li>
     *     <li>Jeśli link nie może być redirectowany, rzuca {@link ShortUrlGoneException}.</li>
     *     <li>Zwraca odpowiedź zawierającą long URL i metadane dla edge.</li>
     * </ol>
     *
     * @param shortCode short code pobrany ze ścieżki URL
     * @param token token wewnętrzny przesłany przez edge/CDN
     * @return odpowiedź z danymi lookupu dla edge/CDN
     */
    @GetMapping("/urls/{shortCode}")
    public ResponseEntity<EdgeLookupResponse> lookup(
            @PathVariable String shortCode,
            @RequestHeader(value = "X-Edge-Token", required = false) String token
    ) {
        /*
         * Najpierw weryfikujemy, czy request pochodzi z autoryzowanej warstwy edge.
         *
         * Ten endpoint jest wewnętrzny i nie powinien być dostępny publicznie bez tokenu.
         */
        verifyToken(token);

        /*
         * Szukamy short code w rozproszonym storage.
         *
         * Jeśli rekord nie istnieje, rzucamy ShortUrlNotFoundException.
         * Globalny exception handler powinien zamienić ten wyjątek na HTTP 404.
         */
        UrlLookupRecord record = distributedUrlStore.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        /*
         * Sprawdzamy, czy link można przekierować.
         *
         * Link jest redirectable tylko wtedy, gdy:
         *
         * - ma status ACTIVE,
         * - nie jest wygasły względem aktualnego czasu.
         */
        boolean redirectable = record.status() == UrlStatus.ACTIVE
                && !record.isExpired(Instant.now(clock));

        /*
         * Dobieramy TTL dla odpowiedzi.
         *
         * Jeśli link jest poprawny i redirectable, można go cache'ować jako pozytywny lookup.
         * Jeśli link jest nieaktywny lub wygasły, wybieramy krótszy/negatywny TTL.
         *
         * Uwaga: w obecnej implementacji przy błędzie poniżej rzucany jest wyjątek,
         * więc negatywny TTL nie zostanie ustawiony w tej odpowiedzi, chyba że
         * globalny exception handler również ustawia odpowiedni Cache-Control.
         */
        Duration ttl = redirectable
                ? regionProperties.getEdgeCacheTtl()
                : regionProperties.getNegativeCacheTtl();

        /*
         * Jeśli link nie może być przekierowany, zwracamy błąd domenowy.
         *
         * Status inny niż ACTIVE oznacza np. BLOCKED, DISABLED albo DELETED.
         * Wygasły link dostaje osobną przyczynę "expired".
         */
        if (!redirectable) {
            if (record.status() != UrlStatus.ACTIVE) {
                throw new ShortUrlGoneException(shortCode, "status=" + record.status());
            }

            throw new ShortUrlGoneException(shortCode, "expired");
        }

        /*
         * Zwracamy pozytywną odpowiedź dla edge.
         *
         * Cache-Control:
         * - max-age(ttl),
         * - public.
         *
         * To pozwala warstwie CDN/edge cache'ować lookup przez określony czas.
         */
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(ttl).cachePublic())
                .body(new EdgeLookupResponse(
                        record.shortCode(),
                        record.longUrl(),
                        record.status(),
                        record.expiresAt(),
                        record.regionId(),
                        ttl.toSeconds(),
                        true
                ));
    }

    /**
     * Weryfikuje token wewnętrzny edge/CDN.
     *
     * <p>
     * Metoda sprawdza dwa warunki:
     * </p>
     *
     * <ol>
     *     <li>czy edge lookup jest włączony w konfiguracji,</li>
     *     <li>czy token z nagłówka {@code X-Edge-Token} zgadza się z tokenem z konfiguracji.</li>
     * </ol>
     *
     * <p>
     * Jeśli którykolwiek warunek nie jest spełniony, rzucany jest
     * {@link AdminUnauthorizedException}.
     * </p>
     *
     * @param token token przesłany w nagłówku requestu
     */
    private void verifyToken(String token) {
        /*
         * Jeśli edge lookup jest wyłączony, endpoint odmawia dostępu nawet wtedy,
         * gdy klient poda poprawny token.
         */
        if (!edgeProperties.isEnabled()) {
            throw new AdminUnauthorizedException("Edge lookup is disabled");
        }

        /*
         * Brak tokenu albo token różny od skonfigurowanego oznacza brak autoryzacji.
         */
        if (token == null || !token.equals(edgeProperties.getInternalToken())) {
            throw new AdminUnauthorizedException("Invalid edge token");
        }
    }
}