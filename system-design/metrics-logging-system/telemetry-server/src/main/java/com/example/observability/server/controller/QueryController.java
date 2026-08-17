package com.example.observability.server.controller;

import com.example.observability.server.auth.Rbac;
import com.example.observability.server.model.LogQueryResult;
import com.example.observability.server.model.MetricPoint;
import com.example.observability.server.planner.QueryPlan;
import com.example.observability.server.planner.QueryPlanner;
import com.example.observability.server.repository.TelemetryRepository;
import com.example.observability.server.util.Validation;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST API do odpytywania danych telemetrycznych.
 *
 * Ten controller obsługuje zapytania użytkownika o:
 * - logi,
 * - metryki,
 * - plany wykonania zapytań.
 *
 * Ważne:
 * Controller nie powinien sam znać szczegółów storage'u.
 * Decyzje typu:
 * - z której tabeli czytać,
 * - czy użyć rollupu,
 * - czy można pominąć scan dzięki bloom filterowi,
 * - jak ograniczyć zakres czasowy,
 * podejmuje QueryPlanner albo TelemetryRepository.
 */
@RestController
@RequestMapping("/api/v1/query")
public class QueryController {

    /**
     * Repozytorium wykonujące fizyczne zapytania do storage'u telemetrycznego.
     *
     * W praktyce to tutaj finalnie trafiają query do ClickHouse:
     * - SELECT logów,
     * - SELECT metryk,
     * - agregacje time series.
     *
     * Controller przekazuje tylko już zwalidowane i znormalizowane parametry.
     */
    private final TelemetryRepository repository;

    /**
     * Query planner decydujący, jak najlepiej wykonać zapytanie.
     *
     * Dla logów może zdecydować np.:
     * - czy użyć bloom filterów,
     * - czy hot scan ma sens,
     * - jakie optymalizacje zastosować.
     *
     * Dla metryk może zdecydować np.:
     * - czy czytać raw samples,
     * - czy użyć rollupu 1m / 5m / 1h,
     * - z której tabeli ClickHouse pobrać dane.
     */
    private final QueryPlanner planner;

    public QueryController(TelemetryRepository repository, QueryPlanner planner) {
        this.repository = repository;
        this.planner = planner;
    }

    /**
     * Odpytuje logi dla danego tenanta.
     *
     * Endpoint:
     * GET /api/v1/query/logs
     *
     * Obsługiwane filtry:
     * - tenantId,
     * - service,
     * - level,
     * - contains,
     * - start,
     * - end,
     * - limit.
     *
     * Domyślny zakres czasu to ostatnia godzina.
     *
     * Przepływ:
     * 1. Sprawdzenie RBAC read dla tenanta.
     * 2. Wyliczenie efektywnego zakresu czasu.
     * 3. Zbudowanie planu query.
     * 4. Jeśli planner wykryje, że bloom filter wyklucza wynik, zwracamy pustą listę.
     * 5. W przeciwnym razie wykonujemy query w repository.
     */
    @GetMapping("/logs")
    public List<LogQueryResult> queryLogs(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String contains,
            @RequestParam(required = false) Instant start,
            @RequestParam(required = false) Instant end,
            @RequestParam(required = false) Integer limit
    ) {
        // Tylko użytkownik z prawem odczytu do tenanta może czytać jego logi.
        Rbac.requireRead(tenantId);

        /*
         * Jeśli klient nie poda zakresu czasu, ograniczamy query do ostatniej godziny.
         * To chroni storage przed przypadkowym odpytywaniem bardzo dużych zakresów.
         */
        Instant now = Instant.now();
        Instant effectiveEnd = end == null ? now : end;
        Instant effectiveStart = start == null ? effectiveEnd.minusSeconds(3600) : start;

        /*
         * Planner analizuje parametry zapytania i zwraca plan wykonania.
         *
         * Dla logów plan może zawierać informacje typu:
         * - czy użyto bloom filtera,
         * - czy query dotyczy hot storage,
         * - jakie optymalizacje są aktywne,
         * - jaki zakres czasu będzie skanowany.
         */
        QueryPlan plan = planner.planLogs(
                tenantId,
                service,
                level,
                contains,
                effectiveStart,
                effectiveEnd,
                limit
        );

        /*
         * Optymalizacja bloom filter negative.
         *
         * Jeśli planner wie, że szukany token na pewno nie występuje
         * w indeksowanych chunkach, nie ma sensu robić kosztownego hot scan.
         *
         * Wtedy zwracamy pustą listę bez uderzania do ClickHouse.
         */
        if (plan.optimizations().contains("bloom-filter-negative-skip-hot-scan")) {
            return List.of();
        }

        // Fizyczne wykonanie query po logach.
        return repository.queryLogs(
                tenantId,
                service,
                level,
                contains,
                effectiveStart,
                effectiveEnd,
                limit
        );
    }

    /**
     * Zwraca plan wykonania zapytania logowego bez pobierania samych logów.
     *
     * Endpoint:
     * GET /api/v1/query/logs/plan
     *
     * Ten endpoint jest przydatny do:
     * - debugowania query,
     * - pokazania użytkownikowi kosztu zapytania,
     * - sprawdzenia, czy użyty zostanie bloom filter,
     * - diagnostyki wolnych zapytań.
     *
     * Nie wykonuje właściwego SELECT-a po logach.
     */
    @GetMapping("/logs/plan")
    public QueryPlan planLogs(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String contains,
            @RequestParam(required = false) Instant start,
            @RequestParam(required = false) Instant end,
            @RequestParam(required = false) Integer limit
    ) {
        Rbac.requireRead(tenantId);

        Instant now = Instant.now();
        Instant effectiveEnd = end == null ? now : end;
        Instant effectiveStart = start == null ? effectiveEnd.minusSeconds(3600) : start;

        return planner.planLogs(
                tenantId,
                service,
                level,
                contains,
                effectiveStart,
                effectiveEnd,
                limit
        );
    }

    /**
     * Odpytuje serię metryczną.
     *
     * Endpoint:
     * GET /api/v1/query/metrics
     *
     * Parametry:
     * - tenantId,
     * - metricName,
     * - service,
     * - minutes,
     * - stepSeconds.
     *
     * Przykład:
     * GET /api/v1/query/metrics?metricName=http_requests_total&service=api&minutes=60&stepSeconds=60
     *
     * Przepływ:
     * 1. Sprawdzenie RBAC read.
     * 2. Ograniczenie zakresu query przez clamp.
     * 3. Wyliczenie start/end.
     * 4. Planner wybiera tabelę raw albo rollup.
     * 5. Repository wykonuje query do wybranej tabeli.
     */
    @GetMapping("/metrics")
    public List<MetricPoint> queryMetrics(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam String metricName,
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(defaultValue = "60") int stepSeconds
    ) {
        Rbac.requireRead(tenantId);

        /*
         * Ograniczamy minutes do zakresu 1 minuta - 365 dni.
         *
         * To zabezpiecza przed skrajnymi wartościami typu:
         * - minutes=0,
         * - minutes=-100,
         * - minutes=999999999.
         */
        int safeMinutes = Validation.clamp(minutes, 1, 60 * 24 * 365);

        /*
         * Ograniczamy step do 1 sekundy - 1 godziny.
         *
         * Zbyt mały step na dużym zakresie może wygenerować ogromną liczbę punktów.
         * Zbyt duży step może nie mieć sensu dla dashboardów.
         */
        int safeStep = Validation.clamp(stepSeconds, 1, 3600);

        Instant end = Instant.now();
        Instant start = end.minusSeconds(safeMinutes * 60L);

        /*
         * Planner wybiera optymalną tabelę.
         *
         * Przykładowo:
         * - krótki zakres + mały step => raw metrics table,
         * - długi zakres + step 60s => rollup_1m,
         * - bardzo długi zakres + step 3600s => rollup_1h.
         */
        QueryPlan plan = planner.planMetrics(
                tenantId,
                metricName,
                service,
                start,
                end,
                safeStep
        );

        /*
         * Repository dostaje nazwę tabeli z planu.
         *
         * To oznacza, że decyzja raw vs rollup nie jest zaszyta w controllerze,
         * tylko w QueryPlannerze.
         */
        return repository.queryMetricSeries(
                plan.tableName(),
                tenantId,
                metricName,
                service,
                start,
                end,
                safeStep
        );
    }

    /**
     * Zwraca plan wykonania zapytania metrycznego bez pobierania punktów.
     *
     * Endpoint:
     * GET /api/v1/query/metrics/plan
     *
     * Użycie:
     * - debugowanie wyboru tabeli,
     * - sprawdzenie, czy użyty będzie rollup,
     * - szacowanie kosztu zapytania,
     * - diagnostyka dashboardów.
     */
    @GetMapping("/metrics/plan")
    public QueryPlan planMetrics(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam String metricName,
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(defaultValue = "60") int stepSeconds
    ) {
        Rbac.requireRead(tenantId);

        int safeMinutes = Validation.clamp(minutes, 1, 60 * 24 * 365);
        int safeStep = Validation.clamp(stepSeconds, 1, 3600);

        Instant end = Instant.now();
        Instant start = end.minusSeconds(safeMinutes * 60L);

        return planner.planMetrics(
                tenantId,
                metricName,
                service,
                start,
                end,
                safeStep
        );
    }
}