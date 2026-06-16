package com.example.observability.server.quota;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serwis egzekwujący limity użycia systemu per tenant.
 *
 * Quoty chronią platformę przed sytuacją, w której jeden tenant
 * przeciąża ingest albo query layer.
 *
 * Ten serwis limituje trzy główne wymiary:
 * - liczbę log events na minutę,
 * - liczbę metric samples na minutę,
 * - liczbę query requests na minutę.
 *
 * Implementacja jest in-memory.
 * Oznacza to, że limity działają lokalnie dla jednej instancji aplikacji.
 * Przy wielu replikach każdy pod ma osobny licznik.
 */
@Service
public class QuotaService {

    /**
     * Konfiguracja limitów.
     *
     * QuotaProperties zwraca limity dla konkretnego tenantId.
     * Może mieć wartości domyślne oraz override'y per tenant.
     */
    private final QuotaProperties properties;

    /**
     * Liczniki użycia.
     *
     * Klucz ma format:
     * tenantId:dimension:minute
     *
     * Przykład:
     * demo:logs:29671234
     *
     * Wartość to bucket z numerem minuty i licznikiem użycia.
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public QuotaService(QuotaProperties properties) {
        this.properties = properties;
    }

    /**
     * Sprawdza quota dla ingestu logów.
     *
     * events oznacza liczbę log eventów w aktualnym batchu.
     *
     * Jeśli tenant przekroczy logsPerMinute,
     * metoda rzuci HTTP 429 Too Many Requests.
     */
    public void checkLogs(String tenantId, int events) {
        check(
                tenantId,
                "logs",
                events,
                properties.forTenant(tenantId).getLogsPerMinute()
        );
    }

    /**
     * Sprawdza quota dla ingestu metryk.
     *
     * samples oznacza liczbę pojedynczych próbek metrycznych,
     * nie liczbę serii.
     *
     * Przykład:
     * 10 serii po 100 próbek = 1000 samples.
     */
    public void checkMetricSamples(String tenantId, int samples) {
        check(
                tenantId,
                "metrics",
                samples,
                properties.forTenant(tenantId).getMetricSamplesPerMinute()
        );
    }

    /**
     * Sprawdza quota dla query API.
     *
     * Każde query kosztuje tutaj 1 jednostkę.
     *
     * To jest prosta polityka MVP.
     * Produkcyjnie koszt query powinien zależeć od zakresu czasu,
     * liczby partycji, limitu, typu query i szacowanej liczby skanowanych danych.
     */
    public void checkQuery(String tenantId) {
        check(
                tenantId,
                "queries",
                1,
                properties.forTenant(tenantId).getQueryRequestsPerMinute()
        );
    }

    /**
     * Zwraca aktywną konfigurację quota dla tenanta.
     *
     * Używane m.in. przez admin API i QueryPlanner.
     */
    public QuotaProperties.TenantQuota tenantQuota(String tenantId) {
        return properties.forTenant(tenantId);
    }

    /**
     * Właściwy mechanizm naliczania limitu.
     *
     * Parametry:
     * - tenantId: tenant, którego limit sprawdzamy,
     * - dimension: typ limitu, np. logs/metrics/queries,
     * - cost: koszt aktualnej operacji,
     * - limit: maksymalny koszt na minutę.
     *
     * Działanie:
     * 1. Wylicza aktualną minutę epoch.
     * 2. Buduje klucz tenant + dimension + minute.
     * 3. Pobiera albo tworzy licznik dla tego bucketu.
     * 4. Dodaje koszt operacji.
     * 5. Jeśli licznik przekroczy limit, rzuca HTTP 429.
     * 6. Czyści stare buckety.
     */
    private void check(String tenantId, String dimension, int cost, int limit) {
        /*
         * Bucket czasowy = aktualna minuta.
         *
         * Instant.now().getEpochSecond() / 60 daje numer minuty od epoch.
         * Dzięki temu wszystkie operacje w tej samej minucie trafiają
         * do tego samego licznika.
         */
        long minute = Instant.now().getEpochSecond() / 60;

        String key = tenantId + ":" + dimension + ":" + minute;

        /*
         * computeIfAbsent jest bezpieczne dla wielu wątków przy ConcurrentHashMap.
         *
         * Jeśli bucket dla danej minuty jeszcze nie istnieje,
         * tworzymy go z licznikiem ustawionym na 0.
         */
        Bucket bucket = buckets.computeIfAbsent(
                key,
                ignored -> new Bucket(minute)
        );

        /*
         * AtomicInteger pozwala bezpiecznie zwiększać licznik
         * przy równoległych requestach.
         *
         * Math.max(0, cost) zabezpiecza przed ujemnym kosztem,
         * który mógłby sztucznie zmniejszyć zużycie.
         */
        int used = bucket.used.addAndGet(Math.max(0, cost));

        /*
         * Po przekroczeniu limitu request jest odrzucany jako HTTP 429.
         *
         * To jest standardowy kod dla rate limit / quota exceeded.
         */
        if (used > limit) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "quota exceeded for "
                            + dimension
                            + ": used="
                            + used
                            + " limit="
                            + limit
            );
        }

        /*
         * Proste czyszczenie starych bucketów.
         *
         * Trzymamy aktualną minutę i około dwie poprzednie.
         * Dzięki temu mapa nie rośnie bez końca.
         *
         * Uwaga:
         * removeIf na całej mapie przy każdym checku jest proste,
         * ale przy bardzo dużym ruchu może być kosztowne.
         */
        buckets.entrySet().removeIf(
                e -> e.getValue().minute < minute - 2
        );
    }

    /**
     * Bucket licznika dla jednej minuty.
     *
     * minute:
     * - numer minuty epoch.
     *
     * used:
     * - wykorzystanie limitu w tej minucie.
     */
    private record Bucket(
            long minute,
            AtomicInteger used
    ) {
        Bucket(long minute) {
            this(minute, new AtomicInteger());
        }
    }
}