package com.example.notification.application;

import com.example.notification.domain.AuditAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serwis odpowiedzialny za rate limiting tworzenia powiadomień.
 *
 * Jego zadaniem jest ograniczenie liczby requestów, które mogą wejść
 * do głównego pipeline’u Notification System.
 *
 * RateLimiterService jest wywoływany na początku NotificationService.create().
 *
 * Dzięki temu system może odrzucić nadmiarowy request zanim wykona droższe operacje:
 * - sprawdzanie idempotencyKey,
 * - wybór kanałów,
 * - walidację template’ów,
 * - zapis Notification,
 * - zapis OutboxEvent.
 *
 * Ta implementacja jest in-memory i działa tylko w ramach jednej instancji aplikacji.
 * W produkcji ten mechanizm powinien być oparty np. o Redis,
 * żeby działał poprawnie przy wielu instancjach serwisu.
 */
@Service
public class RateLimiterService {

    /**
     * Mapa bucketów rate limitera.
     *
     * Klucz bucketu opisuje zakres limitu, np.:
     * - tenant:tenant-a
     * - user:tenant-a:user-123
     *
     * Bucket przechowuje:
     * - ile requestów zostało jeszcze w aktualnym oknie,
     * - kiedy okno się resetuje.
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Limit requestów na minutę dla całego tenanta.
     *
     * Chroni system przed sytuacją, w której jeden tenant generuje zbyt dużo ruchu
     * i degraduje usługę dla innych tenantów.
     */
    private final int tenantLimit;

    /**
     * Limit requestów na minutę dla konkretnego usera w ramach tenanta.
     *
     * Chroni przed spamem albo błędną integracją dotyczącą jednego użytkownika.
     */
    private final int userLimit;

    /**
     * Audyt służy do zapisania informacji o odrzuconych requestach.
     *
     * To jest ważne operacyjnie:
     * jeśli ktoś zgłosi, że powiadomienia nie wychodzą,
     * można sprawdzić, czy requesty nie były blokowane przez rate limiter.
     */
    private final AuditService auditService;

    public RateLimiterService(
            @Value("${notification.rate-limit.tenant-per-minute:60}") int tenantLimit,
            @Value("${notification.rate-limit.user-per-minute:20}") int userLimit,
            AuditService auditService
    ) {
        this.tenantLimit = tenantLimit;
        this.userLimit = userLimit;
        this.auditService = auditService;
    }

    /**
     * Sprawdza limity dla danego requestu.
     *
     * Jeden request musi przejść dwa limity:
     *
     * 1. limit tenanta,
     * 2. limit użytkownika w ramach tenanta.
     *
     * Jeśli którykolwiek limit zostanie przekroczony,
     * metoda rzuci RateLimitExceededException.
     *
     * NotificationService wtedy przerwie tworzenie powiadomienia.
     */
    public void check(String tenantId, String userId) {
        /*
         * Limit globalny dla tenanta.
         *
         * Przykład:
         * tenant-a może utworzyć maksymalnie 60 powiadomień na minutę.
         */
        checkBucket(
                "tenant:" + tenantId,
                tenantLimit,
                tenantId,
                "tenant"
        );

        /*
         * Limit dla konkretnego użytkownika w ramach tenanta.
         *
         * Przykład:
         * user-123 w tenant-a może mieć maksymalnie 20 powiadomień na minutę.
         *
         * Dzięki temu jeden użytkownik nie zużywa całego limitu tenanta.
         */
        checkBucket(
                "user:" + tenantId + ":" + userId,
                userLimit,
                tenantId,
                "user"
        );
    }

    /**
     * Sprawdza pojedynczy bucket rate limitera.
     *
     * To jest prosty fixed-window limiter:
     *
     * - okno trwa 60 sekund,
     * - bucket ma licznik remaining,
     * - każdy zaakceptowany request zmniejsza remaining o 1,
     * - po upływie okna licznik wraca do pełnego limitu.
     *
     * Przykład:
     * limit = 60
     * resetAt = 12:01:00
     * remaining = 10
     *
     * Jeśli request przyjdzie przed 12:01:00, zużywa 1 z remaining.
     * Jeśli request przyjdzie po 12:01:00, bucket resetuje się do 60.
     */
    private void checkBucket(
            String key,
            int limit,
            String tenantId,
            String scope
    ) {
        /*
         * Tworzymy bucket, jeśli jeszcze nie istnieje.
         *
         * computeIfAbsent jest bezpieczne przy równoległym dostępie do mapy.
         *
         * Nowy bucket startuje z pełnym limitem i resetem za 60 sekund.
         */
        Bucket bucket = buckets.computeIfAbsent(
                key,
                ignored -> new Bucket(
                        limit,
                        Instant.now().plusSeconds(60)
                )
        );

        /*
         * Synchronizujemy na konkretnym buckecie.
         *
         * ConcurrentHashMap zabezpiecza samą mapę,
         * ale nie zabezpiecza atomowo operacji na polach bucket.remaining i bucket.resetAt.
         *
         * Bez synchronized dwa równoległe requesty mogłyby jednocześnie zobaczyć
         * remaining > 0 i oba przejść mimo braku dostępnego limitu.
         */
        synchronized (bucket) {
            /*
             * Jeśli aktualne okno czasowe minęło, resetujemy bucket.
             *
             * To jest fixed window, więc po przekroczeniu resetAt użytkownik/tenant
             * dostaje od nowa pełny limit.
             */
            if (Instant.now().isAfter(bucket.resetAt)) {
                bucket.remaining = limit;
                bucket.resetAt = Instant.now().plusSeconds(60);
            }

            /*
             * Jeśli nie ma już dostępnych requestów w aktualnym oknie,
             * odrzucamy request.
             */
            if (bucket.remaining <= 0) {
                /*
                 * Audytujemy odrzucenie przez rate limiter.
                 *
                 * Metadata:
                 * - scope mówi, czy limit dotyczył tenanta czy usera,
                 * - key pozwala zidentyfikować konkretny bucket.
                 */
                auditService.record(
                        tenantId,
                        "system",
                        AuditAction.RATE_LIMIT_REJECTED,
                        null,
                        Map.of(
                                "scope", scope,
                                "key", key
                        )
                );

                /*
                 * Ten wyjątek powinien zostać zmapowany przez warstwę API
                 * na HTTP 429 Too Many Requests.
                 */
                throw new Exceptions.RateLimitExceededException(
                        "Rate limit exceeded for " + scope
                );
            }

            /*
             * Request mieści się w limicie.
             * Zużywamy jeden slot z aktualnego okna.
             */
            bucket.remaining--;
        }
    }

    /**
     * Pojedynczy bucket rate limitera.
     *
     * Bucket reprezentuje jedno okno limitu dla konkretnego klucza,
     * np. konkretnego tenanta albo konkretnego usera.
     */
    private static class Bucket {

        /**
         * Liczba requestów pozostałych w aktualnym oknie.
         */
        private int remaining;

        /**
         * Moment, w którym okno się resetuje.
         */
        private Instant resetAt;

        private Bucket(int remaining, Instant resetAt) {
            this.remaining = remaining;
            this.resetAt = resetAt;
        }
    }
}