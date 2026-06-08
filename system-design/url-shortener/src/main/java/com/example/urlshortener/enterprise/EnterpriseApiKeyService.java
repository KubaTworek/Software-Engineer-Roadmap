package com.example.urlshortener.enterprise;

import com.example.urlshortener.exception.AdminUnauthorizedException;
import com.example.urlshortener.service.RateLimitService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serwis odpowiedzialny za zarządzanie i uwierzytelnianie Enterprise API Keys.
 *
 * <p>
 * Ta klasa obsługuje dwa główne przypadki użycia:
 * </p>
 *
 * <ul>
 *     <li>tworzenie nowego klucza API dla klienta enterprise,</li>
 *     <li>uwierzytelnianie requestów przychodzących z nagłówkiem {@code X-Api-Key}.</li>
 * </ul>
 *
 * <p>
 * Ważna zasada bezpieczeństwa: surowy API key jest zwracany tylko raz — podczas
 * tworzenia. W bazie danych zapisywany jest wyłącznie jego hash, a nie jawna
 * wartość klucza.
 * </p>
 *
 * <p>
 * Dzięki temu, jeśli baza danych zostanie ujawniona, atakujący nie otrzyma od razu
 * działających kluczy API. Nadal jednak ważna jest siła salta/sekretu oraz
 * odpowiednia ochrona konfiguracji.
 * </p>
 *
 * <p>
 * Serwis wykorzystuje także {@link RateLimitService}, aby nakładać limit requestów
 * per API key.
 * </p>
 */
@Service
public class EnterpriseApiKeyService {

    /**
     * Generator kryptograficznie silnych liczb losowych.
     *
     * <p>
     * {@link SecureRandom} jest używany do generowania bajtów API key.
     * Nie należy używać zwykłego {@link java.util.Random} do generowania sekretów,
     * ponieważ nie jest przeznaczony do zastosowań bezpieczeństwa.
     * </p>
     *
     * <p>
     * Pole jest statyczne, ponieważ {@code SecureRandom} może być bezpiecznie
     * współdzielony i nie ma potrzeby tworzyć nowej instancji przy każdym kluczu.
     * </p>
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Repozytorium Enterprise API Keys.
     *
     * <p>
     * Odpowiada za zapis i odczyt encji {@link EnterpriseApiKey}.
     * Klucz jest wyszukiwany po hashu, nie po jawnej wartości API key.
     * </p>
     */
    private final EnterpriseApiKeyRepository repository;

    /**
     * Konfiguracja modułu Enterprise API.
     *
     * <p>
     * Zawiera między innymi:
     * </p>
     *
     * <ul>
     *     <li>flagę włączenia/wyłączenia Enterprise API,</li>
     *     <li>domyślny limit requestów na minutę,</li>
     *     <li>salt/sekret używany do hashowania API key.</li>
     * </ul>
     */
    private final EnterpriseProperties properties;

    /**
     * Serwis rate limitingu.
     *
     * <p>
     * Używany podczas uwierzytelniania klucza, aby ograniczyć liczbę requestów
     * wykonywanych przez konkretny Enterprise API key.
     * </p>
     */
    private final RateLimitService rateLimitService;

    /**
     * Zegar aplikacji.
     *
     * <p>
     * Używany do sprawdzania, czy API key jest nadal aktywny, np. czy nie wygasł.
     * Wstrzyknięcie {@link Clock} ułatwia testowanie czasu.
     * </p>
     */
    private final Clock clock;

    /**
     * Konstruktor serwisu.
     *
     * <p>
     * Spring wstrzykuje zależności przez constructor injection.
     * Dzięki temu klasa jest łatwa do testowania i jawnie pokazuje swoje zależności.
     * </p>
     *
     * @param repository repozytorium API keys
     * @param properties konfiguracja Enterprise API
     * @param rateLimitService serwis rate limitingu
     * @param clock zegar aplikacji
     */
    public EnterpriseApiKeyService(
            EnterpriseApiKeyRepository repository,
            EnterpriseProperties properties,
            RateLimitService rateLimitService,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.clock = clock;
    }

    /**
     * Tworzy nowy Enterprise API key.
     *
     * <p>
     * Metoda jest transakcyjna, ponieważ zapisuje nową encję API key do bazy.
     * </p>
     *
     * <p>
     * Przepływ:
     * </p>
     *
     * <ol>
     *     <li>Generuje losowy surowy API key.</li>
     *     <li>Oblicza hash tego klucza.</li>
     *     <li>Normalizuje tier klienta.</li>
     *     <li>Ustala rate limit per minute.</li>
     *     <li>Zapisuje encję {@link EnterpriseApiKey} do bazy.</li>
     *     <li>Zwraca odpowiedź zawierającą surowy klucz API.</li>
     * </ol>
     *
     * <p>
     * Surowy klucz API jest zwracany w {@link CreateEnterpriseApiKeyResponse},
     * ale nie powinien być zapisywany w bazie. Klient powinien skopiować go
     * od razu po utworzeniu.
     * </p>
     *
     * @param request dane potrzebne do utworzenia API key
     * @return odpowiedź z nowym API key i jego metadanymi
     */
    @Transactional
    public CreateEnterpriseApiKeyResponse create(CreateEnterpriseApiKeyRequest request) {

        /*
         * Generujemy surowy API key.
         *
         * To jest jedyny moment, w którym aplikacja zna jawny klucz.
         * Po zapisaniu do bazy będzie przechowywany tylko jego hash.
         */
        String rawKey = generateApiKey();

        /*
         * Hashujemy API key przed zapisem.
         *
         * W bazie danych nie powinno być surowych API keys.
         */
        String hash = hash(rawKey);

        /*
         * Normalizujemy tier klienta.
         *
         * Jeśli request nie zawiera tieru albo zawiera pusty string,
         * używamy domyślnie ENTERPRISE.
         *
         * W przeciwnym razie:
         * - trimujemy spacje,
         * - zamieniamy na uppercase.
         *
         * Przykład:
         * " premium " -> "PREMIUM"
         */
        String tier = request.tier() == null || request.tier().isBlank()
                ? "ENTERPRISE"
                : request.tier().trim().toUpperCase();

        /*
         * Ustalamy limit requestów na minutę.
         *
         * Jeśli request nie podał własnego limitu, używamy domyślnego limitu
         * z konfiguracji EnterpriseProperties.
         */
        int rateLimit = request.rateLimitPerMinute() == null
                ? properties.getDefaultRateLimitPerMinute()
                : request.rateLimitPerMinute();

        /*
         * Tworzymy i zapisujemy encję EnterpriseApiKey.
         *
         * Do encji trafia hash klucza, nie rawKey.
         */
        EnterpriseApiKey saved = repository.save(new EnterpriseApiKey(
                request.name(),
                hash,
                tier,
                rateLimit,
                request.expiresAt()
        ));

        /*
         * Zwracamy odpowiedź.
         *
         * Uwaga: rawKey znajduje się w odpowiedzi, aby klient mógł go zapisać.
         * Nie będzie później możliwe jego ponowne odczytanie z bazy.
         */
        return new CreateEnterpriseApiKeyResponse(
                saved.getId(),
                saved.getName(),
                rawKey,
                saved.getTier(),
                saved.getRateLimitPerMinute(),
                saved.getExpiresAt(),
                saved.getCreatedAt()
        );
    }

    /**
     * Uwierzytelnia request enterprise na podstawie surowego API key.
     *
     * <p>
     * Metoda jest używana np. przez endpointy przyjmujące nagłówek:
     * </p>
     *
     * <pre>
     * X-Api-Key: us_...
     * </pre>
     *
     * <p>
     * Przepływ:
     * </p>
     *
     * <ol>
     *     <li>Sprawdza, czy Enterprise API jest włączone.</li>
     *     <li>Sprawdza, czy API key został podany.</li>
     *     <li>Hashuje podany klucz.</li>
     *     <li>Szuka klucza w bazie po hashu.</li>
     *     <li>Sprawdza, czy klucz jest aktywny i niewygasły.</li>
     *     <li>Sprawdza rate limit dla danego klucza.</li>
     *     <li>Zwraca {@link EnterprisePrincipal} z metadanymi klienta.</li>
     * </ol>
     *
     * <p>
     * Jeśli którykolwiek warunek nie jest spełniony, metoda rzuca
     * {@link AdminUnauthorizedException}.
     * </p>
     *
     * @param rawKey surowy API key z nagłówka {@code X-Api-Key}
     * @return principal reprezentujący uwierzytelnionego klienta enterprise
     */
    @Transactional(readOnly = true)
    public EnterprisePrincipal authenticate(String rawKey) {

        /*
         * Jeśli Enterprise API jest wyłączone w konfiguracji, odrzucamy request.
         */
        if (!properties.isEnabled()) {
            throw new AdminUnauthorizedException("Enterprise API is disabled");
        }

        /*
         * Brak API key albo pusty API key oznacza brak autoryzacji.
         */
        if (rawKey == null || rawKey.isBlank()) {
            throw new AdminUnauthorizedException("Enterprise API key is missing");
        }

        /*
         * Hashujemy dostarczony API key i szukamy odpowiadającego rekordu w bazie.
         *
         * Ponieważ w bazie trzymamy tylko hash, nie porównujemy rawKey bezpośrednio.
         */
        EnterpriseApiKey key = repository.findByKeyHash(hash(rawKey))
                .orElseThrow(() -> new AdminUnauthorizedException("Enterprise API key is invalid"));

        /*
         * Sprawdzamy, czy klucz jest aktywny.
         *
         * Logika isActive() zwykle obejmuje:
         * - status enabled/disabled,
         * - brak daty wygaśnięcia albo expiresAt w przyszłości.
         */
        if (!key.isActive(Instant.now(clock))) {
            throw new AdminUnauthorizedException("Enterprise API key is inactive or expired");
        }

        /*
         * Nakładamy rate limit per API key.
         *
         * Klucz rate limitingu ma postać:
         *
         * rl:enterprise:{apiKeyId}
         *
         * Limit jest liczony w oknie 1 minuty.
         */
        rateLimitService.checkFixedWindow(
                "rl:enterprise:" + key.getId(),
                key.getRateLimitPerMinute(),
                Duration.ofMinutes(1)
        );

        /*
         * Zwracamy principal z podstawowymi danymi klienta enterprise.
         *
         * Dzięki temu warstwa wyżej może wiedzieć, który klient wykonał request,
         * jaki ma tier i jaki limit.
         */
        return new EnterprisePrincipal(
                key.getId(),
                key.getName(),
                key.getTier(),
                key.getRateLimitPerMinute()
        );
    }

    /**
     * Generuje nowy losowy API key.
     *
     * <p>
     * Klucz składa się z prefiksu:
     * </p>
     *
     * <pre>
     * us_
     * </pre>
     *
     * <p>
     * oraz 32 losowych bajtów zapisanych jako hex.
     * 32 bajty to 256 bitów entropii przed zakodowaniem.
     * </p>
     *
     * <p>
     * Przykładowy format:
     * </p>
     *
     * <pre>
     * us_4f8a...d91c
     * </pre>
     *
     * <p>
     * Prefiks może być użyteczny operacyjnie, np. do rozpoznania typu klucza
     * albo środowiska. Sama tajność pochodzi z losowej części, nie z prefiksu.
     * </p>
     *
     * @return nowy surowy API key
     */
    private String generateApiKey() {
        /*
         * Tworzymy bufor 32 bajtów.
         */
        byte[] bytes = new byte[32];

        /*
         * Wypełniamy bufor kryptograficznie bezpiecznymi losowymi bajtami.
         */
        RANDOM.nextBytes(bytes);

        /*
         * Zwracamy klucz jako prefiks + reprezentacja hex losowych bajtów.
         */
        return "us_" + HexFormat.of().formatHex(bytes);
    }

    /**
     * Hashuje surowy API key przed zapisem lub porównaniem z bazą.
     *
     * <p>
     * Hash jest liczony z wartości:
     * </p>
     *
     * <pre>
     * apiKeyHashSalt + ":" + rawKey
     * </pre>
     *
     * <p>
     * Dzięki temu w bazie danych można przechowywać hash klucza zamiast jego
     * jawnej wartości.
     * </p>
     *
     * <p>
     * Salt z konfiguracji utrudnia ataki słownikowe, ale w produkcyjnym systemie
     * jeszcze lepszym rozwiązaniem byłby HMAC-SHA256 z sekretem aplikacyjnym
     * albo dedykowany mechanizm key hashing.
     * </p>
     *
     * @param rawKey surowy API key
     * @return hash API key w formacie hex
     */
    private String hash(String rawKey) {
        try {
            /*
             * Tworzymy instancję MessageDigest dla SHA-256.
             */
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            /*
             * Budujemy dane wejściowe do hashowania.
             *
             * Jawnie używamy UTF-8, aby wynik nie zależał od domyślnego kodowania JVM.
             */
            byte[] hashed = digest.digest(
                    (properties.getApiKeyHashSalt() + ":" + rawKey)
                            .getBytes(StandardCharsets.UTF_8)
            );

            /*
             * Zamieniamy hash binarny na string hexadecimalny.
             */
            return HexFormat.of().formatHex(hashed);
        } catch (Exception exception) {
            /*
             * SHA-256 powinien być dostępny w każdej standardowej implementacji Javy.
             * Jeśli jednak wystąpi błąd, traktujemy to jako błąd krytyczny aplikacji.
             */
            throw new IllegalStateException("Unable to hash API key", exception);
        }
    }
}