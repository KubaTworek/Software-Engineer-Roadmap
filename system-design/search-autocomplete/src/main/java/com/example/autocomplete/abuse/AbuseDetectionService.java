package com.example.autocomplete.abuse;

import com.example.autocomplete.model.AutocompleteContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prosty moduł abuse detection dla autocomplete.
 *
 * Jego główna rola:
 * - ograniczyć liczbę requestów z jednego IP,
 * - chronić endpoint autocomplete przed scrapingiem,
 * - odciąć klientów generujących zbyt duży ruch,
 * - zmniejszyć ryzyko przeciążenia indeksu, rankingu i cache.
 *
 * To jest implementacja in-memory, dobra edukacyjnie albo dla jednej instancji.
 * W produkcji przy wielu replikach należałoby użyć centralnego storage,
 * np. Redis, rate limiter gatewayowy albo dedykowany abuse service.
 */
@Component
public class AbuseDetectionService {

    /**
     * Maksymalna liczba requestów z jednego IP w ciągu 60 sekund.
     *
     * 120 req/min oznacza średnio 2 requesty na sekundę.
     *
     * Dla autocomplete to dość łagodny limit, bo jeden użytkownik może generować
     * kilka requestów podczas pisania, np.:
     *
     * i -> ip -> iph -> ipho -> iphon
     *
     * Limit ma blokować boty/scrapery, a nie normalne pisanie użytkownika.
     */
    private static final int MAX_REQUESTS_PER_MINUTE = 120;

    /**
     * Historia requestów pogrupowana po IP klienta.
     *
     * Klucz:
     * - IP użytkownika, np. "127.0.0.1"
     *
     * Wartość:
     * - kolejka timestampów requestów z ostatniej minuty.
     *
     * ConcurrentHashMap zabezpiecza dostęp do mapy przy wielu requestach równolegle.
     * Sama kolejka ArrayDeque nie jest thread-safe, dlatego niżej synchronizujemy dostęp
     * do konkretnej kolejki.
     */
    private final Map<String, Deque<Long>> requestsByIp = new ConcurrentHashMap<>();

    /**
     * Sprawdza, czy request z danego kontekstu może zostać obsłużony.
     *
     * Zwraca:
     * - true: request jest dozwolony,
     * - false: request powinien zostać zablokowany.
     *
     * Metoda działa jak sliding window rate limiter:
     * trzymamy timestampy requestów z ostatnich 60 sekund dla danego IP.
     */
    public boolean isAllowed(AutocompleteContext context) {

        /*
         * Aktualny czas w milisekundach.
         *
         * Używamy go do usuwania requestów starszych niż 60 sekund.
         */
        long now = Instant.now().toEpochMilli();

        /*
         * Pobieramy kolejkę requestów dla IP klienta.
         *
         * Jeśli to pierwszy request z tego IP, tworzymy nową kolejkę.
         *
         * context.safeClientIp() zabezpiecza nas przed null/blank IP,
         * np. zwracając "unknown".
         */
        Deque<Long> deque = requestsByIp.computeIfAbsent(
                context.safeClientIp(),
                ignored -> new ArrayDeque<>()
        );

        /*
         * Synchronizujemy dostęp do kolejki konkretnego IP.
         *
         * ConcurrentHashMap chroni mapę, ale nie chroni ArrayDeque.
         * Bez synchronized równoległe requesty z tego samego IP mogłyby
         * uszkodzić stan kolejki albo błędnie policzyć limit.
         */
        synchronized (deque) {

            /*
             * Usuwamy requesty starsze niż 60 sekund.
             *
             * Dzięki temu kolejka reprezentuje tylko aktualne okno czasowe:
             * ostatnie 60 sekund.
             */
            while (!deque.isEmpty() && now - deque.peekFirst() > 60_000) {
                deque.removeFirst();
            }

            /*
             * Jeśli w ostatnich 60 sekundach było już zbyt dużo requestów,
             * blokujemy kolejny request.
             *
             * W tym przypadku nie dodajemy nowego timestampu do kolejki,
             * bo request nie zostanie obsłużony.
             */
            if (deque.size() >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }

            /*
             * Request mieści się w limicie.
             *
             * Zapisujemy jego timestamp i pozwalamy aplikacji kontynuować:
             * cache lookup, indeks, safety filter, ranking itd.
             */
            deque.addLast(now);

            return true;
        }
    }
}