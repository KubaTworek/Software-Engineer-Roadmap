package com.example.urlshortener.analytics;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za szybkie liczniki kliknięć przechowywane w Redisie.
 *
 * <p>
 * Ta klasa jest częścią modułu analytics. Jej zadaniem jest utrzymywanie prostych,
 * szybkich liczników kliknięć dla skróconych linków.
 * </p>
 *
 * <p>
 * Liczniki w Redisie są używane jako szybka warstwa odczytowa, np. dla dashboardu
 * albo statystyk near-real-time. Nie powinny być traktowane jako jedyne źródło
 * prawdy dla analityki historycznej. Źródłem prawdy pozostają zwykle:
 * </p>
 *
 * <ul>
 *     <li>surowe eventy kliknięć w bazie, np. {@code click_events},</li>
 *     <li>agregaty dzienne w bazie, np. {@code url_daily_stats},</li>
 *     <li>system analityczny typu ClickHouse, BigQuery lub warehouse.</li>
 * </ul>
 *
 * <p>
 * Redis jest tutaj optymalizacją wydajnościową. Jeśli Redis jest niedostępny,
 * serwis ignoruje błąd i pozwala reszcie przetwarzania działać dalej.
 * </p>
 */
@Service
public class ClickCounterService {

    /**
     * Czas życia liczników w Redisie.
     *
     * <p>
     * Każdy licznik po zwiększeniu dostaje TTL równy 90 dni.
     * Dzięki temu Redis nie przechowuje liczników w nieskończoność.
     * </p>
     *
     * <p>
     * W obecnej implementacji TTL jest odświeżany przy każdym inkremencie.
     * Oznacza to, że aktywne linki będą utrzymywały swoje liczniki dłużej,
     * a nieaktywne liczniki wygasną po 90 dniach od ostatniego kliknięcia.
     * </p>
     */
    private static final Duration COUNTER_TTL = Duration.ofDays(90);

    /**
     * Springowy klient do pracy z Redisem na wartościach tekstowych.
     *
     * <p>
     * Używany jest {@link StringRedisTemplate}, ponieważ klucze i wartości liczników
     * są proste:
     * </p>
     *
     * <ul>
     *     <li>klucz: {@code String},</li>
     *     <li>wartość: liczba zapisana jako string w Redisie.</li>
     * </ul>
     *
     * <p>
     * Operacja {@code increment()} w Redisie jest atomowa, więc dobrze nadaje się
     * do liczników zwiększanych równolegle przez wielu consumerów.
     * </p>
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * Konstruktor serwisu.
     *
     * <p>
     * Spring wstrzykuje {@link StringRedisTemplate} przez konstruktor.
     * To ułatwia testowanie i jasno pokazuje zależność od Redisa.
     * </p>
     *
     * @param redisTemplate klient Redis dla operacji na stringach
     */
    public ClickCounterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Zwiększa całkowity licznik kliknięć dla danego short code.
     *
     * <p>
     * Klucz Redis ma postać:
     * </p>
     *
     * <pre>
     * analytics:clicks:total:{shortCode}
     * </pre>
     *
     * <p>
     * Przykład:
     * </p>
     *
     * <pre>
     * analytics:clicks:total:aB92xK7
     * </pre>
     *
     * <p>
     * Licznik ten reprezentuje przybliżoną, szybką liczbę wszystkich kliknięć
     * danego skróconego linku w okresie przechowywania w Redisie.
     * </p>
     *
     * @param shortCode kod skróconego linku
     */
    public void incrementTotal(String shortCode) {
        increment("analytics:clicks:total:" + shortCode);
    }

    /**
     * Zwiększa dzienny licznik kliknięć dla danego short code i daty.
     *
     * <p>
     * Klucz Redis ma postać:
     * </p>
     *
     * <pre>
     * analytics:clicks:daily:{shortCode}:{date}
     * </pre>
     *
     * <p>
     * Przykład:
     * </p>
     *
     * <pre>
     * analytics:clicks:daily:aB92xK7:2026-06-07
     * </pre>
     *
     * <p>
     * Ten licznik pozwala szybko odczytać liczbę kliknięć dla konkretnego dnia,
     * bez wykonywania zapytania do bazy danych.
     * </p>
     *
     * @param shortCode kod skróconego linku
     * @param date data agregacji dziennej
     */
    public void incrementDaily(String shortCode, LocalDate date) {
        increment("analytics:clicks:daily:" + shortCode + ":" + date);
    }

    /**
     * Pobiera całkowity licznik kliknięć z Redisa.
     *
     * <p>
     * Metoda zwraca {@link Optional}, ponieważ licznik może nie istnieć.
     * Brak licznika nie musi oznaczać błędu — może oznaczać, że:
     * </p>
     *
     * <ul>
     *     <li>link nie miał jeszcze kliknięć,</li>
     *     <li>licznik wygasł po TTL,</li>
     *     <li>Redis został wyczyszczony,</li>
     *     <li>Redis jest chwilowo niedostępny.</li>
     * </ul>
     *
     * @param shortCode kod skróconego linku
     * @return licznik kliknięć, jeśli istnieje i da się go odczytać
     */
    public Optional<Long> getTotal(String shortCode) {
        return getLong("analytics:clicks:total:" + shortCode);
    }

    /**
     * Pobiera dzienny licznik kliknięć z Redisa.
     *
     * <p>
     * Klucz jest budowany z short code oraz daty.
     * </p>
     *
     * @param shortCode kod skróconego linku
     * @param date dzień, dla którego pobieramy licznik
     * @return dzienny licznik kliknięć, jeśli istnieje i da się go odczytać
     */
    public Optional<Long> getDaily(String shortCode, LocalDate date) {
        return getLong("analytics:clicks:daily:" + shortCode + ":" + date);
    }

    /**
     * Prywatna metoda zwiększająca licznik Redis pod wskazanym kluczem.
     *
     * <p>
     * Wykonuje dwie operacje:
     * </p>
     *
     * <ol>
     *     <li>{@code INCR key} — atomowo zwiększa licznik o 1,</li>
     *     <li>{@code EXPIRE key 90d} — ustawia lub odświeża TTL licznika.</li>
     * </ol>
     *
     * <p>
     * Jeśli Redis jest niedostępny, metoda ignoruje błąd. To celowa decyzja:
     * Redisowe liczniki są optymalizacją, nie krytycznym elementem działania
     * redirectów ani trwałej analityki.
     * </p>
     *
     * @param key klucz Redis licznika
     */
    private void increment(String key) {
        try {
            /*
             * Atomowo zwiększamy licznik w Redisie.
             *
             * Jeśli klucz nie istnieje, Redis utworzy go z wartością 1.
             */
            redisTemplate.opsForValue().increment(key);

            /*
             * Ustawiamy TTL licznika.
             *
             * W obecnej wersji TTL jest odświeżany przy każdym kliknięciu.
             * To oznacza sliding expiration: licznik aktywnego linku będzie
             * żył tak długo, jak długo pojawiają się kliknięcia.
             */
            redisTemplate.expire(key, COUNTER_TTL);
        } catch (DataAccessException ignored) {
            /*
             * Redis nie jest twardą zależnością.
             *
             * Jeśli Redis padnie albo wystąpi timeout, nie przerywamy przetwarzania.
             * Trwałe dane analityczne powinny być zapisane w bazie przez AnalyticsService.
             */
        }
    }

    /**
     * Pobiera wartość liczbową z Redisa i konwertuje ją na {@link Long}.
     *
     * <p>
     * Jeśli klucz nie istnieje, wartość nie jest liczbą albo Redis jest
     * niedostępny, metoda zwraca {@link Optional#empty()}.
     * </p>
     *
     * <p>
     * Dzięki temu warstwa wyżej może łatwo zastosować fallback, np. pobrać dane
     * z bazy danych lub pokazać wartość z agregatów dziennych.
     * </p>
     *
     * @param key klucz Redis
     * @return wartość licznika jako {@link Long}, jeśli jest dostępna
     */
    private Optional<Long> getLong(String key) {
        try {
            /*
             * Pobieramy wartość jako string.
             *
             * StringRedisTemplate przechowuje wartości tekstowo, więc licznik
             * z Redisa również trafia tutaj jako String.
             */
            String value = redisTemplate.opsForValue().get(key);

            /*
             * Brak wartości oznacza brak licznika.
             */
            if (value == null) {
                return Optional.empty();
            }

            /*
             * Konwertujemy string na Long.
             *
             * Jeśli wartość nie jest poprawną liczbą, Long.parseLong() rzuci wyjątek,
             * który zostanie złapany poniżej.
             */
            return Optional.of(Long.parseLong(value));
        } catch (Exception ignored) {
            /*
             * Celowo łapiemy szeroki Exception:
             *
             * - Redis może być niedostępny,
             * - wartość może nie być liczbą,
             * - może wystąpić timeout,
             * - może wystąpić inny błąd klienta Redis.
             *
             * W każdym z tych przypadków traktujemy licznik jako niedostępny.
             */
            return Optional.empty();
        }
    }
}