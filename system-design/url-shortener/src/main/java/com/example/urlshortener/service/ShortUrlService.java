package com.example.urlshortener.service;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.exception.CustomAliasAlreadyExistsException;
import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.region.RegionProperties;
import com.example.urlshortener.storage.DistributedStorageReplicationService;
import com.example.urlshortener.storage.UrlLookupRecord;
import com.example.urlshortener.validation.AliasValidator;
import com.example.urlshortener.validation.UrlValidator;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Główny serwis domenowy odpowiedzialny za obsługę skróconych URL-i.
 *
 * <p>
 * Ta klasa zawiera właściwą logikę biznesową URL shortenera. Kontrolery REST
 * powinny jedynie przyjmować requesty HTTP i delegować pracę właśnie tutaj.
 * </p>
 *
 * <p>
 * Serwis odpowiada między innymi za:
 * </p>
 *
 * <ul>
 *     <li>tworzenie skróconych URL-i,</li>
 *     <li>obsługę automatycznie generowanych short code,</li>
 *     <li>obsługę custom aliasów,</li>
 *     <li>walidację URL-i docelowych,</li>
 *     <li>walidację aliasów zarezerwowanych,</li>
 *     <li>rozwiązywanie short code do long URL,</li>
 *     <li>integrację z cache,</li>
 *     <li>obsługę wygasania linków,</li>
 *     <li>blokowanie i odblokowywanie linków,</li>
 *     <li>publikację zmian do distributed storage / globalnej warstwy lookup.</li>
 * </ul>
 *
 * <p>
 * Klasa używa transakcji Springa:
 * </p>
 *
 * <ul>
 *     <li>operacje zapisujące są oznaczone jako {@code @Transactional},</li>
 *     <li>operacje tylko do odczytu jako {@code @Transactional(readOnly = true)}.</li>
 * </ul>
 */
@Service
public class ShortUrlService {

    /**
     * Repozytorium skróconych URL-i.
     *
     * <p>
     * Używane do zapisu, odczytu, sprawdzania istnienia short code oraz pobierania
     * kolejnego ID z sekwencji.
     * </p>
     */
    private final ShortUrlRepository repository;

    /**
     * Encoder Base62 używany do zamiany numerycznego ID na krótki kod.
     *
     * <p>
     * Przykład:
     * </p>
     *
     * <pre>
     * 1000000 -> 4c92
     * </pre>
     *
     * <p>
     * Dzięki temu automatycznie generowane short code są krótsze niż surowe liczby.
     * </p>
     */
    private final Base62Encoder base62Encoder;

    /**
     * Walidator długiego URL-a.
     *
     * <p>
     * Odpowiada za sprawdzenie, czy URL docelowy jest poprawnym publicznym URL-em
     * HTTP/HTTPS i czy nie wskazuje np. na localhost albo adres prywatny.
     * </p>
     */
    private final UrlValidator urlValidator;

    /**
     * Walidator custom aliasów.
     *
     * <p>
     * Sprawdza między innymi, czy alias nie jest zarezerwowaną ścieżką systemową,
     * np. {@code api}, {@code admin}, {@code metrics}.
     * </p>
     */
    private final AliasValidator aliasValidator;

    /**
     * Serwis cache dla lookupów short code -> long URL.
     *
     * <p>
     * Używany przede wszystkim w ścieżce redirectu. Cache pozwala uniknąć odpytywania
     * bazy danych dla popularnych linków.
     * </p>
     */
    private final ShortUrlCacheService cacheService;

    /**
     * Zegar aplikacji.
     *
     * <p>
     * Używany do sprawdzania wygaśnięcia linków oraz ustawiania czasu blokady.
     * Wstrzyknięcie {@link Clock} ułatwia testowanie logiki czasowej.
     * </p>
     */
    private final Clock clock;

    /**
     * Publiczny bazowy URL usługi, np.:
     *
     * <pre>
     * https://sho.rt
     * </pre>
     *
     * <p>
     * Używany do budowania pełnego short URL zwracanego w odpowiedziach API.
     * </p>
     */
    private final String publicBaseUrl;

    /**
     * Konfiguracja regionu aplikacji.
     *
     * <p>
     * Używana do sprawdzenia, czy bieżący region może przyjmować operacje zapisu,
     * oraz do oznaczania regionu w rekordach replikowanych do distributed storage.
     * </p>
     */
    private final RegionProperties regionProperties;

    /**
     * Serwis publikujący zmiany do warstwy distributed storage / globalnego lookupu.
     *
     * <p>
     * Po utworzeniu, zablokowaniu lub odblokowaniu linku serwis publikuje upsert
     * rekordu lookupu, żeby inne regiony albo edge mogły dostać aktualne dane.
     * </p>
     */
    private final DistributedStorageReplicationService replicationService;

    /**
     * Konstruktor serwisu.
     *
     * <p>
     * Wszystkie zależności są przekazywane przez constructor injection.
     * Dzięki temu klasa jest łatwiejsza do testowania i jasno pokazuje, od czego zależy.
     * </p>
     */
    public ShortUrlService(
            ShortUrlRepository repository,
            Base62Encoder base62Encoder,
            UrlValidator urlValidator,
            AliasValidator aliasValidator,
            ShortUrlCacheService cacheService,
            Clock clock,
            @Value("${app.public-base-url}") String publicBaseUrl,
            RegionProperties regionProperties,
            DistributedStorageReplicationService replicationService
    ) {
        this.repository = repository;
        this.base62Encoder = base62Encoder;
        this.urlValidator = urlValidator;
        this.aliasValidator = aliasValidator;
        this.cacheService = cacheService;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl;
        this.regionProperties = regionProperties;
        this.replicationService = replicationService;
    }

    /**
     * Tworzy nowy skrócony URL.
     *
     * <p>
     * Metoda obsługuje dwa tryby:
     * </p>
     *
     * <ul>
     *     <li>custom alias — użytkownik podał własny alias,</li>
     *     <li>generated code — system generuje short code automatycznie z ID.</li>
     * </ul>
     *
     * <p>
     * Przepływ:
     * </p>
     *
     * <ol>
     *     <li>Sprawdza, czy bieżący region akceptuje zapisy.</li>
     *     <li>Waliduje long URL.</li>
     *     <li>Normalizuje custom alias.</li>
     *     <li>Jeśli alias istnieje, waliduje go i tworzy URL z aliasem.</li>
     *     <li>Jeśli aliasu nie ma, generuje short code automatycznie.</li>
     * </ol>
     *
     * @param request request zawierający long URL, opcjonalny custom alias i opcjonalne expiresAt
     * @return odpowiedź z utworzonym short URL
     */
    @Transactional
    public CreateShortUrlResponse create(CreateShortUrlRequest request) {
        /*
         * W architekturze multi-region zapisy powinny trafiać wyłącznie do regionu,
         * który przyjmuje zapisy. W trybie active-passive będzie to primary region.
         */
        ensureRegionAcceptsWrites();

        /*
         * Walidujemy i normalizujemy docelowy URL.
         *
         * UrlValidator odrzuca m.in. nieobsługiwane schematy, localhost,
         * prywatne IP oraz URL-e z user info.
         */
        URI normalizedUrl = urlValidator.validatePublicHttpUrl(request.longUrl());

        /*
         * Normalizujemy custom alias.
         *
         * Jeśli alias nie został podany, metoda zwróci null.
         * Jeśli został podany, zostanie przycięty i sprowadzony do lowercase.
         */
        String customAlias = normalizeAlias(request.customAlias());

        /*
         * Jeśli użytkownik podał custom alias, obsługujemy osobną ścieżkę tworzenia.
         */
        if (customAlias != null) {
            /*
             * Sprawdzamy, czy alias nie jest zarezerwowaną nazwą systemową.
             */
            aliasValidator.validateNotReserved(customAlias);

            /*
             * Tworzymy rekord z aliasem użytkownika jako shortCode.
             */
            return createWithCustomAlias(normalizedUrl, customAlias, request.expiresAt());
        }

        /*
         * Jeśli aliasu nie ma, tworzymy URL z automatycznie wygenerowanym code.
         */
        return createWithGeneratedCode(normalizedUrl, request.expiresAt());
    }

    /**
     * Rozwiązuje short code do long URL.
     *
     * <p>
     * Jest to krytyczna metoda dla endpointu redirectu.
     * Najpierw próbuje odczytać long URL z cache. Jeśli cache nie ma wpisu,
     * odczytuje rekord z bazy, waliduje jego status i expiration, a następnie
     * zapisuje wynik do cache.
     * </p>
     *
     * @param shortCode kod skróconego linku
     * @return docelowy long URL
     */
    @Transactional(readOnly = true)
    public String resolveLongUrl(String shortCode) {
        /*
         * Najpierw próbujemy cache.
         *
         * Jeśli cache ma wartość, nie pytamy bazy.
         * Jeśli cache nie ma wartości, wykonujemy fallback do bazy.
         */
        return cacheService.getLongUrl(shortCode)
                .orElseGet(() -> resolveFromDatabaseAndCache(shortCode));
    }

    /**
     * Pobiera szczegóły short URL-a.
     *
     * <p>
     * Używane przez endpoint API zwracający metadane linku.
     * </p>
     *
     * @param shortCode kod skróconego linku
     * @return szczegóły URL-a jako DTO
     */
    @Transactional(readOnly = true)
    public UrlDetailsResponse getDetails(String shortCode) {
        /*
         * Szukamy rekordu w bazie.
         *
         * Brak rekordu oznacza 404 na poziomie API.
         */
        ShortUrl entity = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        /*
         * Mapujemy encję na DTO odpowiedzi.
         */
        return toDetailsResponse(entity);
    }

    /**
     * Blokuje wskazany short code.
     *
     * <p>
     * Blokada jest używana np. dla linków phishingowych, malware, spamowych
     * albo automatycznie wykrytych jako nadużycie.
     * </p>
     *
     * <p>
     * Po blokadzie:
     * </p>
     *
     * <ul>
     *     <li>status linku powinien zostać ustawiony na BLOCKED,</li>
     *     <li>powód blokady powinien zostać zapisany,</li>
     *     <li>cache musi zostać unieważniony,</li>
     *     <li>zmiana musi zostać opublikowana do distributed storage.</li>
     * </ul>
     *
     * @param shortCode kod linku do zablokowania
     * @param reason powód blokady
     * @return szczegóły URL-a po blokadzie
     */
    @Transactional
    public UrlDetailsResponse block(String shortCode, String reason) {
        /*
         * Pobieramy encję. Brak linku oznacza 404.
         */
        ShortUrl entity = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        /*
         * Delegujemy zmianę stanu do encji domenowej.
         *
         * Encja powinna ustawić status BLOCKED, blockedReason i blockedAt.
         */
        entity.block(reason, Instant.now(clock));

        /*
         * Zapisujemy zmianę w bazie.
         */
        ShortUrl saved = repository.save(entity);

        /*
         * Usuwamy wpis z cache.
         *
         * To krytyczne: zablokowany link nie może dalej działać tylko dlatego,
         * że Redis ma stary wpis shortCode -> longUrl.
         */
        cacheService.evict(shortCode);

        /*
         * Publikujemy zmianę do distributed storage/globalnego lookupu.
         */
        replicate(saved);

        /*
         * Zwracamy aktualne dane linku.
         */
        return toDetailsResponse(saved);
    }

    /**
     * Odblokowuje wskazany short code.
     *
     * <p>
     * Po odblokowaniu link może znów działać, jeśli nie jest wygasły.
     * </p>
     *
     * @param shortCode kod linku do odblokowania
     * @return szczegóły URL-a po odblokowaniu
     */
    @Transactional
    public UrlDetailsResponse unblock(String shortCode) {
        /*
         * Pobieramy encję z bazy.
         */
        ShortUrl entity = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        /*
         * Zmieniamy stan encji na odblokowany.
         *
         * Encja powinna przywrócić status ACTIVE i wyczyścić dane blokady,
         * o ile taka jest przyjęta reguła domenowa.
         */
        entity.unblock();

        /*
         * Zapisujemy zmianę.
         */
        ShortUrl saved = repository.save(entity);

        /*
         * Jeśli link nie jest wygasły, możemy ponownie wpisać go do cache.
         * Jeśli jest wygasły, upewniamy się, że cache jest pusty.
         */
        if (!saved.isExpired(Instant.now(clock))) {
            cacheService.putLongUrl(saved.getShortCode(), saved.getLongUrl(), saved.getExpiresAt());
        } else {
            cacheService.evict(shortCode);
        }

        /*
         * Replikujemy aktualny stan do distributed storage.
         */
        replicate(saved);

        /*
         * Zwracamy szczegóły linku.
         */
        return toDetailsResponse(saved);
    }

    /**
     * Tworzy short URL z automatycznie wygenerowanym short code.
     *
     * <p>
     * Algorytm:
     * </p>
     *
     * <ol>
     *     <li>Pobiera kolejne ID z bazy.</li>
     *     <li>Koduje ID do Base62.</li>
     *     <li>Tworzy encję ShortUrl.</li>
     *     <li>Zapisuje encję do bazy.</li>
     *     <li>Dodaje wpis do cache, jeśli aktywny.</li>
     *     <li>Publikuje rekord do distributed storage.</li>
     *     <li>Zwraca DTO odpowiedzi.</li>
     * </ol>
     */
    private CreateShortUrlResponse createWithGeneratedCode(URI normalizedUrl, Instant expiresAt) {
        /*
         * Pobieramy kolejne ID z sekwencji/repozytorium.
         *
         * To ID jest podstawą do wygenerowania krótkiego kodu.
         */
        Long id = repository.nextId();

        /*
         * Kodujemy ID do Base62.
         */
        String shortCode = base62Encoder.encode(id);

        /*
         * Tworzymy encję z jawnie nadanym ID i shortCode.
         */
        ShortUrl entity = new ShortUrl(id, shortCode, normalizedUrl.toString(), expiresAt);

        /*
         * Zapisujemy encję do bazy i wymuszamy flush.
         *
         * saveAndFlush pozwala szybciej wykryć ewentualne naruszenia constraintów.
         */
        ShortUrl saved = repository.saveAndFlush(entity);

        /*
         * Jeśli link jest aktywny i niewygasły, wpisujemy go do cache.
         */
        cacheIfActive(saved);

        /*
         * Publikujemy rekord lookupu do distributed storage.
         */
        replicate(saved);

        /*
         * Zwracamy odpowiedź API.
         */
        return toCreateResponse(saved);
    }

    /**
     * Tworzy short URL z custom aliasem użytkownika.
     *
     * <p>
     * Custom alias jest używany bezpośrednio jako shortCode.
     * </p>
     *
     * @param normalizedUrl poprawny docelowy URL
     * @param customAlias alias użytkownika po normalizacji
     * @param expiresAt opcjonalna data wygaśnięcia
     * @return odpowiedź z utworzonym URL-em
     */
    private CreateShortUrlResponse createWithCustomAlias(
            URI normalizedUrl,
            String customAlias,
            Instant expiresAt
    ) {
        /*
         * Szybkie sprawdzenie, czy alias już istnieje.
         *
         * To daje przyjazny błąd przed próbą insertu, ale nie wystarcza
         * jako ochrona przed race condition.
         */
        if (repository.existsByShortCode(customAlias)) {
            throw new CustomAliasAlreadyExistsException(customAlias);
        }

        try {
            /*
             * Pobieramy ID dla nowego rekordu.
             */
            Long id = repository.nextId();

            /*
             * Tworzymy encję, gdzie shortCode = customAlias.
             */
            ShortUrl entity = new ShortUrl(id, customAlias, normalizedUrl.toString(), expiresAt);

            /*
             * Zapisujemy encję.
             *
             * Jeśli dwa requesty równolegle próbują utworzyć ten sam alias,
             * unikalny constraint w bazie powinien złapać konflikt.
             */
            ShortUrl saved = repository.saveAndFlush(entity);

            /*
             * Cache aktywnego linku.
             */
            cacheIfActive(saved);

            /*
             * Publikacja do distributed storage.
             */
            replicate(saved);

            /*
             * Odpowiedź API.
             */
            return toCreateResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            /*
             * Obsługa race condition.
             *
             * Nawet jeśli existsByShortCode() zwróciło false, inny request mógł
             * w międzyczasie zapisać taki sam alias.
             *
             * Naruszenie unikalnego constraintu mapujemy na domenowy wyjątek
             * CustomAliasAlreadyExistsException.
             */
            throw new CustomAliasAlreadyExistsException(customAlias);
        }
    }

    /**
     * Fallback do bazy danych przy cache missie.
     *
     * <p>
     * Metoda:
     * </p>
     *
     * <ol>
     *     <li>szuka short code w bazie,</li>
     *     <li>waliduje, czy link można przekierować,</li>
     *     <li>wpisuje long URL do cache,</li>
     *     <li>zwraca long URL.</li>
     * </ol>
     */
    private String resolveFromDatabaseAndCache(String shortCode) {
        /*
         * Szukamy linku w bazie.
         */
        ShortUrl entity = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        /*
         * Sprawdzamy status i expiration.
         *
         * Jeśli link nie jest aktywny, metoda rzuci ShortUrlGoneException.
         */
        validateResolvable(entity, shortCode);

        /*
         * Skoro link jest poprawny, dodajemy go do cache.
         */
        cacheService.putLongUrl(entity.getShortCode(), entity.getLongUrl(), entity.getExpiresAt());

        /*
         * Zwracamy long URL.
         */
        return entity.getLongUrl();
    }

    /**
     * Waliduje, czy link może zostać użyty do redirectu.
     *
     * <p>
     * Link może zostać przekierowany tylko wtedy, gdy:
     * </p>
     *
     * <ul>
     *     <li>ma status ACTIVE,</li>
     *     <li>nie jest wygasły.</li>
     * </ul>
     *
     * @param entity encja ShortUrl
     * @param shortCode short code używany w komunikacie błędu
     */
    private void validateResolvable(ShortUrl entity, String shortCode) {
        /*
         * Jeśli status nie jest ACTIVE, usuwamy cache i rzucamy 410 Gone.
         */
        if (entity.getStatus() != UrlStatus.ACTIVE) {
            cacheService.evict(shortCode);
            throw new ShortUrlGoneException(shortCode, "status=" + entity.getStatus());
        }

        /*
         * Jeśli link wygasł, również usuwamy cache i rzucamy 410 Gone.
         */
        Instant now = Instant.now(clock);
        if (entity.isExpired(now)) {
            cacheService.evict(shortCode);
            throw new ShortUrlGoneException(shortCode, "expired");
        }
    }

    /**
     * Dodaje link do cache, jeśli jest aktywny i niewygasły.
     *
     * <p>
     * Używane po utworzeniu lub odblokowaniu linku.
     * </p>
     */
    private void cacheIfActive(ShortUrl saved) {
        if (saved.getStatus() == UrlStatus.ACTIVE && !saved.isExpired(Instant.now(clock))) {
            cacheService.putLongUrl(
                    saved.getShortCode(),
                    saved.getLongUrl(),
                    saved.getExpiresAt()
            );
        }
    }

    /**
     * Mapuje encję ShortUrl na odpowiedź po utworzeniu linku.
     *
     * @param saved zapisana encja
     * @return DTO odpowiedzi create
     */
    private CreateShortUrlResponse toCreateResponse(ShortUrl saved) {
        return new CreateShortUrlResponse(
                saved.getId(),
                saved.getShortCode(),
                buildShortUrl(saved.getShortCode()),
                saved.getLongUrl(),
                saved.getExpiresAt(),
                saved.getCreatedAt()
        );
    }

    /**
     * Mapuje encję ShortUrl na szczegółową odpowiedź API.
     *
     * <p>
     * Odpowiedź zawiera również informacje o blokadzie, jeśli link został zablokowany.
     * </p>
     */
    private UrlDetailsResponse toDetailsResponse(ShortUrl entity) {
        return new UrlDetailsResponse(
                entity.getId(),
                entity.getShortCode(),
                buildShortUrl(entity.getShortCode()),
                entity.getLongUrl(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getBlockedReason(),
                entity.getBlockedAt()
        );
    }

    /**
     * Buduje pełny publiczny short URL na podstawie short code.
     *
     * <p>
     * Przykład:
     * </p>
     *
     * <pre>
     * publicBaseUrl = https://sho.rt
     * shortCode     = abc123
     * result        = https://sho.rt/abc123
     * </pre>
     *
     * @param shortCode kod skróconego linku
     * @return pełny publiczny short URL
     */
    private String buildShortUrl(String shortCode) {
        return UriComponentsBuilder.fromUriString(publicBaseUrl)
                .pathSegment(shortCode)
                .build()
                .toUriString();
    }

    /**
     * Sprawdza, czy bieżący region może przyjmować operacje zapisu.
     *
     * <p>
     * W architekturze multi-region z trybem active-passive zwykle tylko primary
     * region powinien obsługiwać zapisy. Regiony secondary powinny obsługiwać
     * głównie odczyty.
     * </p>
     *
     * <p>
     * Jeśli bieżący region nie jest primary, metoda rzuca wyjątek.
     * </p>
     */
    private void ensureRegionAcceptsWrites() {
        if (!regionProperties.isPrimaryRegion()) {
            throw new IllegalArgumentException(
                    "This region does not accept writes. Primary region: "
                            + regionProperties.getPrimaryRegion()
            );
        }
    }

    /**
     * Publikuje aktualny stan short URL do distributed storage.
     *
     * <p>
     * Distributed storage może służyć do szybkiego globalnego lookupu,
     * replikacji między regionami albo zasilania edge/CDN.
     * </p>
     *
     * <p>
     * Publikowany rekord zawiera:
     * </p>
     *
     * <ul>
     *     <li>shortCode,</li>
     *     <li>longUrl,</li>
     *     <li>status,</li>
     *     <li>expiresAt,</li>
     *     <li>regionId,</li>
     *     <li>updatedAt.</li>
     * </ul>
     */
    private void replicate(ShortUrl entity) {
        replicationService.publishUpsert(new UrlLookupRecord(
                entity.getShortCode(),
                entity.getLongUrl(),
                entity.getStatus(),
                entity.getExpiresAt(),
                regionProperties.getRegionId(),
                entity.getUpdatedAt()
        ));
    }

    /**
     * Normalizuje custom alias użytkownika.
     *
     * <p>
     * Jeśli alias jest pusty albo null, metoda zwraca {@code null}, co oznacza,
     * że system powinien wygenerować short code automatycznie.
     * </p>
     *
     * <p>
     * Jeśli alias istnieje, metoda:
     * </p>
     *
     * <ul>
     *     <li>usuwa białe znaki z początku i końca,</li>
     *     <li>zamienia alias na lowercase.</li>
     * </ul>
     *
     * <p>
     * Dzięki temu aliasy {@code Promo}, {@code promo} i {@code PROMO}
     * są traktowane jako ten sam alias.
     * </p>
     */
    private String normalizeAlias(String customAlias) {
        if (customAlias == null || customAlias.isBlank()) {
            return null;
        }

        return customAlias.trim().toLowerCase(Locale.ROOT);
    }
}