package com.example.urlshortener.service;

import com.example.urlshortener.exception.RateLimitExceededException;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za sprawdzanie limitów liczby requestów.
 *
 * <p>
 * Ta implementacja używa Redisa jako współdzielonego magazynu liczników.
 * Dzięki temu rate limiting działa poprawnie również wtedy, gdy aplikacja
 * działa na wielu instancjach.
 * </p>
 *
 * <p>
 * Serwis implementuje prosty algorytm fixed window:
 * </p>
 *
 * <pre>
 * limit requestów / okno czasowe
 * </pre>
 *
 * <p>
 * Przykład:
 * </p>
 *
 * <pre>
 * 100 requestów / 1 minuta
 * </pre>
 *
 * <p>
 * Dla danego klucza Redis zlicza requesty w aktualnym oknie czasowym.
 * Gdy licznik przekroczy limit, metoda rzuca {@link RateLimitExceededException}.
 * </p>
 *
 * <p>
 * Ważne: ta klasa działa w trybie fail-open. Jeśli Redis jest niedostępny,
 * request nie jest blokowany. System loguje problem i pozwala requestowi przejść.
 * To jest świadoma decyzja: awaria Redisa nie powinna zatrzymać całego API.
 * </p>
 */
@Service
public class RateLimitService {

    /**
     * Logger diagnostyczny.
     *
     * <p>
     * Używany do logowania problemów z Redisem podczas sprawdzania limitu.
     * </p>
     */
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    /**
     * Klient Redis do pracy na wartościach tekstowych.
     *
     * <p>
     * Redis przechowuje licznik requestów jako wartość liczbową pod konkretnym
     * kluczem, np.:
     * </p>
     *
     * <pre>
     * rl:create:203.0.113.10
     * rl:redirect:203.0.113.10
     * rl:enterprise:123
     * </pre>
     *
     * <p>
     * Operacja {@code increment()} w Redisie jest atomowa, więc jest bezpieczna
     * przy równoległych requestach z wielu instancji aplikacji.
     * </p>
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * Konstruktor serwisu.
     *
     * <p>
     * Spring wstrzykuje {@link StringRedisTemplate} przez constructor injection.
     * </p>
     *
     * @param redisTemplate klient Redis
     */
    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Sprawdza limit requestów w algorytmie fixed window.
     *
     * <p>
     * Metoda zwiększa licznik Redis dla podanego klucza i sprawdza, czy licznik
     * przekroczył dozwolony limit w aktualnym oknie czasowym.
     * </p>
     *
     * <p>
     * Przykład użycia:
     * </p>
     *
     * <pre>
     * checkFixedWindow("rl:create:203.0.113.10", 10, Duration.ofMinutes(1));
     * </pre>
     *
     * <p>
     * Oznacza:
     * </p>
     *
     * <pre>
     * maksymalnie 10 requestów na minutę dla klienta 203.0.113.10
     * </pre>
     *
     * <p>
     * Przepływ działania:
     * </p>
     *
     * <ol>
     *     <li>Jeśli limit jest mniejszy lub równy zero, rate limiting jest pomijany.</li>
     *     <li>Zwiększa licznik w Redisie przez {@code INCR}.</li>
     *     <li>Jeśli licznik właśnie powstał, ustawia TTL równy długości okna.</li>
     *     <li>Jeśli licznik przekroczył limit, pobiera TTL i rzuca wyjątek 429.</li>
     *     <li>Jeśli Redis jest niedostępny, loguje błąd i przepuszcza request.</li>
     * </ol>
     *
     * @param key klucz Redis identyfikujący limitowaną operację i klienta
     * @param limit maksymalna liczba requestów w oknie czasowym
     * @param window długość okna czasowego
     * @throws RateLimitExceededException jeśli limit został przekroczony
     */
    public void checkFixedWindow(String key, long limit, Duration window) {

        /*
         * Jeśli limit jest mniejszy lub równy zero, traktujemy to jako wyłączony limit.
         *
         * Dzięki temu konfiguracja może świadomie wyłączyć rate limiting dla danej
         * operacji bez usuwania całego mechanizmu.
         */
        if (limit <= 0) {
            return;
        }

        try {
            /*
             * Atomowo zwiększamy licznik requestów w Redisie.
             *
             * Redis INCR:
             * - jeśli klucz nie istnieje, tworzy go z wartością 1,
             * - jeśli istnieje, zwiększa wartość o 1.
             */
            Long current = redisTemplate.opsForValue().increment(key);

            /*
             * Jeśli current == 1, oznacza to, że właśnie rozpoczęło się nowe okno.
             *
             * Ustawiamy TTL klucza na długość okna czasowego.
             * Po wygaśnięciu klucza Redis usunie licznik, a następny request
             * rozpocznie nowe okno.
             */
            if (current != null && current == 1L) {
                redisTemplate.expire(key, window);
            }

            /*
             * Jeśli licznik przekroczył limit, request powinien zostać odrzucony.
             *
             * Przykład:
             * limit = 10
             * current = 11
             *
             * Jedenasty request w tym samym oknie dostanie błąd rate limitingu.
             */
            if (current != null && current > limit) {

                /*
                 * Pobieramy pozostały TTL klucza.
                 *
                 * TTL mówi, za ile sekund aktualne okno wygaśnie i klient będzie
                 * mógł ponownie wykonywać requesty.
                 */
                Long ttl = redisTemplate.getExpire(key);

                /*
                 * Jeśli Redis nie zwrócił poprawnego TTL, używamy długości okna
                 * jako wartości fallback.
                 *
                 * getExpire() może zwrócić wartości ujemne, np. gdy klucz nie istnieje
                 * albo nie ma ustawionego TTL.
                 */
                long retryAfter = ttl == null || ttl < 0
                        ? window.toSeconds()
                        : ttl;

                /*
                 * Rzucamy wyjątek domenowy.
                 *
                 * Globalny exception handler powinien zamienić go na HTTP:
                 *
                 * 429 Too Many Requests
                 *
                 * oraz najlepiej ustawić nagłówek:
                 *
                 * Retry-After: retryAfter
                 */
                throw new RateLimitExceededException(
                        "Too many requests. Try again later.",
                        retryAfter
                );
            }
        } catch (RateLimitExceededException exception) {
            /*
             * Przekroczenie limitu nie jest błędem infrastruktury.
             *
             * Tego wyjątku nie wolno połknąć w catch RuntimeException,
             * bo wtedy request mimo przekroczenia limitu przeszedłby dalej.
             */
            throw exception;
        } catch (RuntimeException exception) {
            /*
             * Błąd Redisa lub klienta Redis.
             *
             * Serwis działa w trybie fail-open:
             * - logujemy błąd,
             * - nie blokujemy requestu.
             *
             * To zmniejsza ryzyko globalnej awarii API z powodu problemu z Redisem,
             * ale oznacza, że podczas awarii Redisa rate limiting chwilowo nie działa.
             */
            log.warn(
                    "Redis rate-limit check failed for key={}. Failing open.",
                    key,
                    exception
            );
        }
    }
}