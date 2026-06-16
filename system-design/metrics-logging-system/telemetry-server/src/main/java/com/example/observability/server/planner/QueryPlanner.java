package com.example.observability.server.planner;

import com.example.observability.server.bloom.LogBloomFilterService;
import com.example.observability.server.quota.QuotaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Planner zapytań dla logów i metryk.
 *
 * Ta klasa nie wykonuje zapytań do bazy.
 * Jej zadaniem jest przygotować plan wykonania:
 *
 * - sprawdzić quota query,
 * - sprawdzić maksymalny zakres czasu,
 * - zdecydować, czy dane są w hot storage czy cold storage,
 * - wybrać tabelę dla metryk: raw albo rollup,
 * - użyć bloom filterów dla logów,
 * - zwrócić opis optymalizacji i filtrów.
 *
 * QueryController używa tego planu przed wywołaniem TelemetryRepository.
 */
@Service
public class QueryPlanner {

    /**
     * Liczba dni trzymanych w hot storage.
     *
     * Jeśli query kończy się przed granicą hot retention,
     * planner oznacza je jako cold-object-storage.
     *
     * Konfiguracja:
     * telemetry.retention.hot-days
     */
    private final int hotRetentionDays;

    /**
     * Serwis bloom filterów dla logów.
     *
     * Używany do optymalizacji zapytań typu:
     * contains = "timeout"
     *
     * Jeśli bloom filter mówi, że tokenu na pewno nie ma,
     * planner może oznaczyć query jako możliwe do pominięcia.
     */
    private final LogBloomFilterService bloomFilterService;

    /**
     * Serwis quota.
     *
     * Używany do:
     * - sprawdzenia, czy tenant może wykonać kolejne query,
     * - pobrania maksymalnego dozwolonego okna czasowego.
     */
    private final QuotaService quotaService;

    public QueryPlanner(
            @Value("${telemetry.retention.hot-days:30}") int hotRetentionDays,
            LogBloomFilterService bloomFilterService,
            QuotaService quotaService
    ) {
        this.hotRetentionDays = hotRetentionDays;
        this.bloomFilterService = bloomFilterService;
        this.quotaService = quotaService;
    }

    /**
     * Buduje plan wykonania zapytania po logach.
     *
     * Parametry odpowiadają filtrom z QueryController:
     * - tenantId,
     * - service,
     * - level,
     * - contains,
     * - start,
     * - end,
     * - limit.
     *
     * Plan nie zwraca samych logów.
     * Zwraca informację, jak takie query powinno zostać wykonane.
     */
    public QueryPlan planLogs(
            String tenantId,
            String service,
            String level,
            String contains,
            Instant start,
            Instant end,
            Integer limit
    ) {
        /*
         * Query quota jest sprawdzana na początku.
         *
         * Dzięki temu kosztowne query może zostać odrzucone
         * zanim planner zacznie wykonywać dodatkowe sprawdzenia.
         */
        quotaService.checkQuery(tenantId);

        /*
         * Walidacja okna czasowego.
         *
         * Chroni system przed zapytaniami typu:
         * - start po end,
         * - zakres większy niż quota tenanta.
         */
        enforceWindow(tenantId, start, end);

        long seconds = Duration.between(start, end).toSeconds();

        /*
         * Wybór tieru storage.
         *
         * Jeśli całe query kończy się przed granicą hot retention,
         * uznajemy, że powinno iść do cold object storage.
         *
         * W tej implementacji to tylko decyzja w planie.
         * QueryController nadal wykonuje hot query, jeśli nie ma osobnej ścieżki cold read.
         */
        String tier = end.isBefore(
                Instant.now().minus(Duration.ofDays(hotRetentionDays))
        )
                ? "cold-object-storage"
                : "hot-clickhouse";

        List<String> opts = new ArrayList<>();

        /*
         * Podstawowa optymalizacja dla każdego query po czasie.
         *
         * Storage logów powinien być partycjonowany/orderowany po czasie,
         * więc zakres start/end ogranicza skan.
         */
        opts.add("time-partition-pruning");

        /*
         * Filtry service/level zwykle dobrze współpracują z order key
         * albo indeksami pomocniczymi w ClickHouse.
         */
        if (service != null && !service.isBlank()) {
            opts.add("service-order-key-pruning");
        }

        if (level != null && !level.isBlank()) {
            opts.add("level-order-key-pruning");
        }

        /*
         * Bloom filter sprawdza, czy szukany token może wystąpić
         * w danym zakresie tenant/service/level/time.
         *
         * Jeśli contains jest pusty, mightHaveTerm zwraca true,
         * ale optymalizacja bloom nie jest dodawana do planu.
         */
        boolean bloomMatch = bloomFilterService.mightHaveTerm(
                tenantId,
                service,
                level,
                start,
                end,
                contains
        );

        if (contains != null && !contains.isBlank()) {
            opts.add(
                    bloomMatch
                            ? "bloom-filter-positive-scan-candidates"
                            : "bloom-filter-negative-skip-hot-scan"
            );
        }

        /*
         * Filtry są dołączane do planu głównie dla debugowania,
         * endpointów /plan i UI.
         */
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("service", service);
        filters.put("level", level);
        filters.put("contains", contains);
        filters.put("limit", limit);

        return new QueryPlan(
                "logs",
                tenantId,
                start,
                end,
                tier,
                "hot-clickhouse".equals(tier) ? "logs" : "object://logs",
                partitions(start, end),
                seconds,
                opts,
                filters
        );
    }

    /**
     * Buduje plan wykonania zapytania po metrykach.
     *
     * Najważniejsza decyzja:
     * czy czytać raw samples, czy jedną z tabel rollupowych.
     *
     * Dzięki temu długie query nie musi skanować ogromnej liczby punktów raw.
     */
    public QueryPlan planMetrics(
            String tenantId,
            String metricName,
            String service,
            Instant start,
            Instant end,
            int stepSeconds
    ) {
        quotaService.checkQuery(tenantId);
        enforceWindow(tenantId, start, end);

        long seconds = Duration.between(start, end).toSeconds();

        /*
         * Wybór tabeli zależy od długości okna i kroku agregacji.
         *
         * Im dłuższe query albo większy step, tym bardziej opłaca się rollup.
         */
        String table = chooseMetricTable(seconds, stepSeconds);

        List<String> opts = new ArrayList<>();
        opts.add("time-partition-pruning");
        opts.add("metric-name-order-key-pruning");

        if (!"metrics_samples".equals(table)) {
            opts.add("downsampled-rollup-table");
        }

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("metricName", metricName);
        filters.put("service", service);
        filters.put("stepSeconds", stepSeconds);

        String tier = end.isBefore(
                Instant.now().minus(Duration.ofDays(hotRetentionDays))
        )
                ? "cold-object-storage"
                : "hot-clickhouse";

        return new QueryPlan(
                "metrics",
                tenantId,
                start,
                end,
                tier,
                table,
                partitions(start, end),
                seconds,
                opts,
                filters
        );
    }

    /**
     * Wybiera tabelę metryk na podstawie zakresu czasu i stepSeconds.
     *
     * Reguły:
     * - bardzo długie query albo step >= 1h -> rollup 1h,
     * - query > 2 dni albo step >= 5m -> rollup 5m,
     * - query > 6h albo step >= 1m -> rollup 1m,
     * - krótkie query i mały step -> raw samples.
     *
     * To jest prosta heurystyka.
     * Produkcyjnie można uwzględnić jeszcze:
     * - dostępność rollupów,
     * - koszt skanu,
     * - rozdzielczość oczekiwaną przez UI,
     * - cardinality metryki.
     */
    public String chooseMetricTable(long windowSeconds, int stepSeconds) {
        if (windowSeconds > 14L * 24 * 3600 || stepSeconds >= 3600) {
            return "metrics_rollup_1h";
        }

        if (windowSeconds > 2L * 24 * 3600 || stepSeconds >= 300) {
            return "metrics_rollup_5m";
        }

        if (windowSeconds > 6L * 3600 || stepSeconds >= 60) {
            return "metrics_rollup_1m";
        }

        return "metrics_samples";
    }

    /**
     * Sprawdza, czy okno query mieści się w limicie tenanta.
     *
     * Warunki odrzucenia:
     * - end jest przed start,
     * - zakres jest większy niż maxQueryWindowSeconds z quota.
     *
     * Ta metoda rzuca IllegalArgumentException.
     * Produkcyjnie lepiej byłoby rzucać wyjątek mapowany na HTTP 400 albo 422.
     */
    private void enforceWindow(String tenantId, Instant start, Instant end) {
        long seconds = Duration.between(start, end).toSeconds();
        long max = quotaService
                .tenantQuota(tenantId)
                .getMaxQueryWindowSeconds();

        if (seconds < 0 || seconds > max) {
            throw new IllegalArgumentException(
                    "query window exceeds tenant quota: "
                            + seconds
                            + "s > "
                            + max
                            + "s"
            );
        }
    }

    /**
     * Szacuje liczbę partycji dziennych dotykanych przez query.
     *
     * To jest metadana do QueryPlan.
     *
     * Nie służy tutaj do fizycznego wyznaczenia partycji,
     * ale pomaga ocenić koszt query w UI/debug endpointach.
     */
    private int partitions(Instant start, Instant end) {
        long days = Math.max(
                1,
                Duration.between(start, end).toDays() + 1
        );

        return (int) Math.min(Integer.MAX_VALUE, days);
    }
}