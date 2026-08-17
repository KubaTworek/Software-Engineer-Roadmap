package com.example.urlshortener.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za cache'owanie mapowania shortCode -> longUrl w Redisie.
 *
 * <p>
 * Jest to bardzo ważny komponent w ścieżce redirectu. Redirect jest operacją
 * wykonywaną bardzo często, więc każdorazowe odpytywanie bazy danych byłoby
 * kosztowne i zwiększałoby opóźnienia.
 * </p>
 *
 * <p>
 * Ten serwis przechowuje w Redisie proste mapowanie:
 * </p>
 *
 * <pre>
 * url:{shortCode} -> {longUrl}
 * </pre>
 *
 * <p>
 * Przykład:
 * </p>
 *
 * <pre>
 * url:aB92xK7 -> https://example.com/landing-page
 * </pre>
 *
 * <p>
 * Redis jest tutaj traktowany jako warstwa optymalizacyjna, a nie jako źródło
 * prawdy. Źródłem prawdy pozostaje baza danych. Jeśli Redis jest niedostępny,
 * serwis zwraca pusty wynik przy odczycie, a wyższa warstwa może wykonać fallback
 * do bazy danych.
 * </p>
 *
 * <p>
 * Klasa dba także o to, aby TTL wpisu w cache nie był dłuższy niż czas pozostały
 * do wygaśnięcia linku. Dzięki temu wygasły link nie powinien dalej działać tylko
 * dlatego, że jego stary wpis nadal istnieje w Redisie.
 * </p>
 */
@Service
public class ShortUrlCacheService {

    /**
     * Logger używany do zapisywania problemów z Redisem.
     *
     * <p>
     * Błędy cache są logowane jako warning, ale nie przerywają działania systemu.
     * To celowe: awaria Redisa powinna pogorszyć wydajność, ale nie zatrzymać
     * redirectów ani tworzenia linków.
     * </p>
     */
    private static final Logger log = LoggerFactory.getLogger(ShortUrlCacheService.class);

    /**
     * Prefiks kluczy Redis dla mapowania shortCode -> longUrl.
     *
     * <p>
     * Dzięki prefiksowi klucze są uporządkowane i mniej podatne na kolizje
     * z innymi typami danych w Redisie.
     * </p>
     *
     * <p>
     * Finalny klucz ma postać:
     * </p>
     *
     * <pre>
     * url:{shortCode}
     * </pre>
     */
    private static final String KEY_PREFIX = "url:";

    /**
     * Springowy klient do pracy z Redisem na stringach.
     *
     * <p>
     * Używamy {@link StringRedisTemplate}, ponieważ zarówno klucz, jak i wartość
     * są zwykłymi stringami:
     * </p>
     *
     * <ul>
     *     <li>klucz: {@code url:aB92xK7},</li>
     *     <li>wartość: {@code https://example.com/...}.</li>
     * </ul>
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * Zegar aplikacji.
     *
     * <p>
     * Używany przy obliczaniu czasu pozostałego do wygaśnięcia linku.
     * Wstrzyknięcie {@link Clock} ułatwia testowanie logiki TTL.
     * </p>
     */
    private final Clock clock;

    /**
     * Domyślny TTL wpisów cache.
     *
     * <p>
     * Wartość jest pobierana z konfiguracji:
     * </p>
     *
     * <pre>
     * app.cache.url-ttl
     * </pre>
     *
     * <p>
     * Jeśli konfiguracja nie zawiera tej właściwości, używana jest wartość domyślna:
     * </p>
     *
     * <pre>
     * PT24H
     * </pre>
     *
     * <p>
     * {@code PT24H} to format ISO-8601 oznaczający 24 godziny.
     * </p>
     */
    private final Duration defaultTtl;

    /**
     * Konstruktor serwisu cache.
     *
     * <p>
     * Spring wstrzykuje klienta Redis, zegar oraz wartość TTL z konfiguracji.
     * </p>
     *
     * @param redisTemplate klient Redis dla stringów
     * @param clock zegar aplikacji
     * @param defaultTtl domyślny TTL wpisów short URL cache
     */
    public ShortUrlCacheService(
            StringRedisTemplate redisTemplate,
            Clock clock,
            @Value("${app.cache.url-ttl:PT24H}") Duration defaultTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.defaultTtl = defaultTtl;
    }

    /**
     * Pobiera docelowy long URL z cache na podstawie short code.
     *
     * <p>
     * Metoda próbuje odczytać wartość z Redisa spod klucza:
     * </p>
     *
     * <pre>
     * url:{shortCode}
     * </pre>
     *
     * <p>
     * Jeśli wartość istnieje, zwraca ją jako {@link Optional#of(Object)}.
     * Jeśli nie istnieje, zwraca {@link Optional#empty()}.
     * </p>
     *
     * <p>
     * Jeśli Redis jest niedostępny albo operacja odczytu rzuci wyjątek, metoda
     * również zwraca {@link Optional#empty()}. Dzięki temu wyższa warstwa,
     * np. {@code ShortUrlService}, może wykonać fallback do bazy danych.
     * </p>
     *
     * @param shortCode kod skróconego linku
     * @return long URL z cache albo pusty Optional
     */
    public Optional<String> getLongUrl(String shortCode) {
        try {
            /*
             * Budujemy klucz Redis i próbujemy pobrać wartość.
             *
             * Jeśli Redis zwróci null, Optional.ofNullable() zamieni to na Optional.empty().
             */
            return Optional.ofNullable(redisTemplate.opsForValue().get(key(shortCode)));
        } catch (RuntimeException exception) {
            /*
             * Redis jest optymalizacją, nie źródłem prawdy.
             *
             * Przy błędzie odczytu logujemy problem i pozwalamy wyższej warstwie
             * pobrać dane z bazy.
             */
            log.warn(
                    "Redis read failed for shortCode={}. Falling back to database.",
                    shortCode,
                    exception
            );
            return Optional.empty();
        }
    }

    /**
     * Zapisuje mapowanie shortCode -> longUrl w Redisie.
     *
     * <p>
     * TTL wpisu jest wyliczany na podstawie:
     * </p>
     *
     * <ul>
     *     <li>domyślnego TTL z konfiguracji,</li>
     *     <li>opcjonalnego {@code expiresAt} linku.</li>
     * </ul>
     *
     * <p>
     * Jeśli link wygasa wcześniej niż domyślny TTL, cache dostanie krótszy TTL,
     * tak aby nie przechowywać long URL dłużej niż link jest ważny.
     * </p>
     *
     * @param shortCode kod skróconego linku
     * @param longUrl docelowy URL
     * @param expiresAt opcjonalna data wygaśnięcia linku
     */
    public void putLongUrl(String shortCode, String longUrl, Instant expiresAt) {

        /*
         * Wyliczamy TTL wpisu cache.
         *
         * Jeśli expiresAt jest null, użyty zostanie defaultTtl.
         * Jeśli link wygasa wcześniej niż defaultTtl, użyty zostanie czas do wygaśnięcia.
         */
        Duration ttl = calculateTtl(expiresAt);

        /*
         * Jeśli TTL jest zerowy albo ujemny, link już wygasł albo wygasa dokładnie teraz.
         *
         * W takim przypadku nie zapisujemy go do cache. Dla bezpieczeństwa usuwamy
         * ewentualny istniejący wpis.
         */
        if (ttl.isZero() || ttl.isNegative()) {
            evict(shortCode);
            return;
        }

        try {
            /*
             * Zapisujemy long URL do Redisa z wyliczonym TTL.
             *
             * Po upływie TTL Redis automatycznie usunie wpis.
             */
            redisTemplate.opsForValue().set(key(shortCode), longUrl, ttl);
        } catch (RuntimeException exception) {
            /*
             * Błąd zapisu do cache nie powinien zatrzymać operacji biznesowej.
             *
             * Dane są już albo będą dostępne w bazie danych. Brak cache oznacza
             * jedynie gorszą wydajność przy kolejnych redirectach.
             */
            log.warn(
                    "Redis write failed for shortCode={}. Continuing without cache.",
                    shortCode,
                    exception
            );
        }
    }

    /**
     * Usuwa wpis short code z cache.
     *
     * <p>
     * Ta metoda jest używana np. przy:
     * </p>
     *
     * <ul>
     *     <li>blokowaniu linku,</li>
     *     <li>wygaszeniu linku,</li>
     *     <li>wykryciu, że link nie jest już aktywny,</li>
     *     <li>ręcznej invalidacji cache.</li>
     * </ul>
     *
     * <p>
     * Usunięcie cache jest szczególnie ważne przy blokadach bezpieczeństwa.
     * Jeśli link został zablokowany jako phishing, stary wpis cache nie może
     * dalej umożliwiać redirectu.
     * </p>
     *
     * @param shortCode kod skróconego linku
     */
    public void evict(String shortCode) {
        try {
            /*
             * Usuwamy klucz z Redisa.
             *
             * Jeśli klucz nie istnieje, Redis potraktuje to jako operację bez efektu.
             */
            redisTemplate.delete(key(shortCode));
        } catch (RuntimeException exception) {
            /*
             * Logujemy błąd invalidacji.
             *
             * To jest bardziej wrażliwe niż zwykły błąd zapisu cache, ponieważ
             * nieudane usunięcie może zostawić stary wpis. Przy blokadach linków
             * warto mieć dodatkowe mechanizmy invalidacji albo krótkie TTL.
             */
            log.warn("Redis eviction failed for shortCode={}", shortCode, exception);
        }
    }

    /**
     * Oblicza TTL wpisu cache.
     *
     * <p>
     * Reguła:
     * </p>
     *
     * <ul>
     *     <li>jeśli link nie ma daty wygaśnięcia, użyj domyślnego TTL,</li>
     *     <li>jeśli link ma datę wygaśnięcia, użyj minimum z:
     *         <ul>
     *             <li>domyślnego TTL,</li>
     *             <li>czasu pozostałego do wygaśnięcia linku.</li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * <p>
     * Dzięki temu cache nie powinien przeżyć samego linku.
     * </p>
     *
     * @param expiresAt data wygaśnięcia linku albo null
     * @return TTL wpisu cache
     */
    private Duration calculateTtl(Instant expiresAt) {
        /*
         * Brak daty wygaśnięcia oznacza, że możemy użyć domyślnego TTL.
         */
        if (expiresAt == null) {
            return defaultTtl;
        }

        /*
         * Liczymy czas od teraz do expiresAt.
         */
        Duration timeToExpiry = Duration.between(Instant.now(clock), expiresAt);

        /*
         * Jeśli link wygasa wcześniej niż wynosi defaultTtl, używamy krótszego TTL.
         */
        if (timeToExpiry.compareTo(defaultTtl) < 0) {
            return timeToExpiry;
        }

        /*
         * W przeciwnym razie używamy domyślnego TTL.
         */
        return defaultTtl;
    }

    /**
     * Buduje klucz Redis dla danego short code.
     *
     * <p>
     * Przykład:
     * </p>
     *
     * <pre>
     * shortCode = aB92xK7
     * key       = url:aB92xK7
     * </pre>
     *
     * @param shortCode kod skróconego linku
     * @return klucz Redis
     */
    private String key(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}