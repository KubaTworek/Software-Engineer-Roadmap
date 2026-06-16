package com.example.observability.server.controller;

import com.example.observability.server.auth.Rbac;
import com.example.observability.server.cardinality.CardinalityGuard;
import com.example.observability.server.fulltext.FullTextIndexService;
import com.example.observability.server.phase3.AnomalyDetector;
import com.example.observability.server.phase3.CorrelationService;
import com.example.observability.server.region.MultiRegionService;
import com.example.observability.server.repository.TelemetryRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * API dla funkcji Fazy 3 systemu observability.
 *
 * Ten controller grupuje bardziej zaawansowane funkcje platformy:
 *
 * - multi-region,
 * - replication health,
 * - failover plan,
 * - korelację logów, metryk i trace'ów,
 * - anomaly detection,
 * - raporty kardynalności metryk,
 * - planowanie full-text search,
 * - query trace spanów.
 *
 * To nie jest podstawowe API ingest/query.
 * Ten controller wystawia funkcje analityczne i operatorskie
 * budowane na danych już zapisanych w systemie.
 */
@RestController
@RequestMapping("/api/v1/phase3")
public class Phase3Controller {

    /**
     * Serwis obsługujący funkcje multi-region.
     *
     * Odpowiada za:
     * - topologię regionów,
     * - stan replikacji,
     * - heartbeat replikacji,
     * - plan failover.
     */
    private final MultiRegionService multiRegionService;

    /**
     * Serwis korelacji danych telemetrycznych.
     *
     * Łączy dane z różnych źródeł:
     * - metryki,
     * - logi,
     * - trace spans.
     *
     * Dzięki temu można przejść od problemu na metryce
     * do logów i trace'ów z tego samego okna czasowego.
     */
    private final CorrelationService correlationService;

    /**
     * Detektor anomalii dla metryk.
     *
     * W tej wersji wspiera co najmniej:
     * - rolling z-score,
     * - MAD, czyli Median Absolute Deviation.
     *
     * Działa na serii punktów metrycznych pobranych z repository.
     */
    private final AnomalyDetector anomalyDetector;

    /**
     * Repozytorium telemetryczne.
     *
     * Controller używa go bezpośrednio tam, gdzie potrzebny jest odczyt danych:
     * - seria metryczna do anomaly detection,
     * - historia anomaly events,
     * - trace spans.
     */
    private final TelemetryRepository repository;

    /**
     * Guard kardynalności metryk.
     *
     * Tutaj używany nie do walidacji ingestu,
     * ale do generowania raportu kardynalności dla konkretnej metryki.
     */
    private final CardinalityGuard cardinalityGuard;

    /**
     * Serwis opcjonalnego full-text indexu dla logów.
     *
     * Ten controller nie wykonuje pełnego searcha,
     * tylko pokazuje plan wyszukiwania:
     * - czy index może być użyty,
     * - jaki zakres czasu będzie analizowany,
     * - jak query zostanie zinterpretowane.
     */
    private final FullTextIndexService fullTextIndexService;

    public Phase3Controller(
            MultiRegionService multiRegionService,
            CorrelationService correlationService,
            AnomalyDetector anomalyDetector,
            TelemetryRepository repository,
            CardinalityGuard cardinalityGuard,
            FullTextIndexService fullTextIndexService
    ) {
        this.multiRegionService = multiRegionService;
        this.correlationService = correlationService;
        this.anomalyDetector = anomalyDetector;
        this.repository = repository;
        this.cardinalityGuard = cardinalityGuard;
        this.fullTextIndexService = fullTextIndexService;
    }

    /**
     * Zwraca globalną topologię regionów platformy.
     *
     * Endpoint:
     * GET /api/v1/phase3/regions/topology
     *
     * Wymaga platform admina, bo topologia regionów jest informacją globalną,
     * a nie tenant-scoped.
     *
     * Typowe dane:
     * - primary region,
     * - secondary regions,
     * - tryb replikacji,
     * - aktywne regiony,
     * - status regionów.
     */
    @GetMapping("/regions/topology")
    public MultiRegionService.Topology topology() {
        Rbac.requirePlatformAdmin();
        return multiRegionService.topology();
    }

    /**
     * Zwraca stan replikacji dla konkretnego tenanta.
     *
     * Endpoint:
     * GET /api/v1/phase3/regions/replication/health?tenantId=demo
     *
     * Wymaga prawa odczytu do tenanta.
     *
     * Użycie:
     * - dashboard operatorski,
     * - sprawdzenie opóźnienia replikacji,
     * - walidacja czy dane danego tenanta są aktualne w regionie zapasowym.
     */
    @GetMapping("/regions/replication/health")
    public MultiRegionService.ReplicationHealth replicationHealth(
            @RequestParam(defaultValue = "demo") String tenantId
    ) {
        Rbac.requireRead(tenantId);
        return multiRegionService.health(tenantId);
    }

    /**
     * Rejestruje heartbeat replikacji dla danego strumienia.
     *
     * Endpoint:
     * POST /api/v1/phase3/regions/replication/heartbeat
     *
     * Parametry:
     * - tenantId,
     * - targetRegion,
     * - streamName,
     * - lagMs,
     * - status,
     * - details.
     *
     * To endpoint administracyjny.
     * Powinien być wywoływany przez proces replikacji albo operatora,
     * a nie przez zwykłego użytkownika.
     *
     * lagMs pozwala ocenić, jak bardzo region docelowy jest opóźniony
     * względem regionu źródłowego.
     */
    @PostMapping("/regions/replication/heartbeat")
    public void replicationHeartbeat(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam String targetRegion,
            @RequestParam(defaultValue = "logs") String streamName,
            @RequestParam(defaultValue = "0") long lagMs,
            @RequestParam(defaultValue = "ok") String status,
            @RequestParam(defaultValue = "manual heartbeat") String details
    ) {
        Rbac.requireAdmin(tenantId);

        multiRegionService.heartbeat(
                tenantId,
                targetRegion,
                streamName,
                lagMs,
                status,
                details
        );
    }

    /**
     * Zwraca plan failover dla konkretnego tenanta.
     *
     * Endpoint:
     * GET /api/v1/phase3/regions/failover-plan?tenantId=demo
     *
     * Wymaga admina tenanta, bo failover dotyczy dostępności danych
     * i może wpływać na routing zapytań albo ingestu.
     *
     * Plan nie musi od razu wykonywać failovera.
     * Może jedynie pokazywać:
     * - aktualny primary region,
     * - region zapasowy,
     * - opóźnienie replikacji,
     * - ryzyko utraty danych,
     * - zalecaną akcję.
     */
    @GetMapping("/regions/failover-plan")
    public Object failoverPlan(
            @RequestParam(defaultValue = "demo") String tenantId
    ) {
        Rbac.requireAdmin(tenantId);
        return multiRegionService.failoverPlan(tenantId);
    }

    /**
     * Koreluje spike metryki z logami i trace'ami.
     *
     * Endpoint:
     * GET /api/v1/phase3/correlate/metric-logs-traces
     *
     * Parametry:
     * - tenantId,
     * - service,
     * - metricName,
     * - around,
     * - windowSeconds.
     *
     * Użycie:
     * "Metryka latency/error rate wzrosła o 12:00.
     *  Pokaż logi i trace'y z okna +/- N sekund."
     *
     * Jeśli around nie zostanie podany, używany jest aktualny czas.
     */
    @GetMapping("/correlate/metric-logs-traces")
    public CorrelationService.MetricLogCorrelation correlate(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam String service,
            @RequestParam String metricName,
            @RequestParam(required = false) Instant around,
            @RequestParam(defaultValue = "300") int windowSeconds
    ) {
        Rbac.requireRead(tenantId);

        return correlationService.correlateMetricSpikeWithLogs(
                tenantId,
                service,
                metricName,
                around == null ? Instant.now() : around,
                windowSeconds
        );
    }

    /**
     * Koreluje dane telemetryczne po traceId.
     *
     * Endpoint:
     * GET /api/v1/phase3/correlate/trace/{traceId}?tenantId=demo
     *
     * Zwraca powiązane dane dla jednego requestu/trace'a:
     * - trace spans,
     * - logi zawierające ten traceId,
     * - potencjalnie metadane serwisu i czasu wykonania.
     *
     * To jest kluczowy endpoint do debugowania pojedynczego requestu
     * przechodzącego przez wiele usług.
     */
    @GetMapping("/correlate/trace/{traceId}")
    public CorrelationService.TraceCorrelation correlateTrace(
            @RequestParam(defaultValue = "demo") String tenantId,
            @PathVariable String traceId
    ) {
        Rbac.requireRead(tenantId);
        return correlationService.correlateByTraceId(tenantId, traceId);
    }

    /**
     * Uruchamia detekcję anomalii dla najnowszych punktów metryki.
     *
     * Endpoint:
     * GET /api/v1/phase3/anomaly
     *
     * Parametry:
     * - tenantId,
     * - metricName,
     * - service,
     * - minutes,
     * - method.
     *
     * Obsługiwane metody:
     * - zscore,
     * - mad.
     *
     * Przepływ:
     * 1. Pobiera serię metryczną z ostatnich N minut.
     * 2. Przekazuje punkty do detektora anomalii.
     * 3. Zwraca ocenę, czy ostatni punkt wygląda anomalnie.
     *
     * To jest analiza on-demand, a nie cykliczny alert.
     */
    @GetMapping("/anomaly")
    public AnomalyDetector.AnomalyResult anomaly(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam String metricName,
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "360") int minutes,
            @RequestParam(defaultValue = "zscore") String method
    ) {
        Rbac.requireRead(tenantId);

        /*
         * Pobieramy dane z repository w kroku 60 sekund.
         *
         * W tej metodzie zakres nie jest clampowany.
         * Produkcyjnie warto ograniczyć minutes, np. do 1-10080 minut,
         * żeby użytkownik nie wymusił ogromnego query.
         */
        var points = repository.queryMetricSeries(
                tenantId,
                metricName,
                service,
                Instant.now().minusSeconds(minutes * 60L),
                Instant.now(),
                60
        );

        if ("mad".equalsIgnoreCase(method)) {
            return anomalyDetector.detectLatestMad(
                    tenantId,
                    metricName,
                    service,
                    points
            );
        }

        return anomalyDetector.detectLatestZScore(
                tenantId,
                metricName,
                service,
                points
        );
    }

    /**
     * Zwraca zapisane eventy anomalii.
     *
     * Endpoint:
     * GET /api/v1/phase3/anomaly/events?tenantId=demo&limit=100
     *
     * To nie uruchamia detekcji.
     * Ten endpoint tylko odczytuje historię wcześniej wykrytych anomalii.
     *
     * Eventy mogły zostać zapisane przez:
     * - cykliczny anomaly detection job,
     * - alert evaluator,
     * - ręczne uruchomienie detekcji, jeśli implementacja zapisuje wynik.
     */
    @GetMapping("/anomaly/events")
    public Object anomalyEvents(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        Rbac.requireRead(tenantId);
        return repository.queryAnomalyEvents(tenantId, limit);
    }

    /**
     * Zwraca raport kardynalności dla konkretnej metryki.
     *
     * Endpoint:
     * GET /api/v1/phase3/cardinality/report
     *
     * Parametry:
     * - tenantId,
     * - metricName,
     * - hours.
     *
     * Raport pomaga odpowiedzieć na pytania:
     * - ile unikalnych serii generuje metryka,
     * - które labele powodują wzrost kardynalności,
     * - czy metryka jest ryzykowna kosztowo,
     * - czy należy blokować konkretne label values.
     *
     * To jest funkcja diagnostyczna dla advanced cardinality control.
     */
    @GetMapping("/cardinality/report")
    public CardinalityGuard.CardinalityReport cardinality(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam String metricName,
            @RequestParam(defaultValue = "24") int hours
    ) {
        Rbac.requireRead(tenantId);
        return cardinalityGuard.report(tenantId, metricName, hours);
    }

    /**
     * Zwraca plan użycia opcjonalnego full-text indexu.
     *
     * Endpoint:
     * GET /api/v1/phase3/fulltext/plan
     *
     * Parametry:
     * - tenantId,
     * - service,
     * - level,
     * - query,
     * - start,
     * - end.
     *
     * Ten endpoint nie musi zwracać samych logów.
     * Jego celem jest pokazanie, jak full-text query zostanie obsłużone:
     * - czy index istnieje,
     * - ile tokenów będzie szukanych,
     * - jaki zakres czasu zostanie użyty,
     * - czy można ograniczyć liczbę skanowanych chunków.
     */
    @GetMapping("/fulltext/plan")
    public FullTextIndexService.FullTextSearchPlan fullTextPlan(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam String query,
            @RequestParam(required = false) Instant start,
            @RequestParam(required = false) Instant end
    ) {
        Rbac.requireRead(tenantId);

        return fullTextIndexService.plan(
                tenantId,
                service,
                level,
                query,
                start == null ? Instant.now().minusSeconds(3600) : start,
                end == null ? Instant.now() : end
        );
    }

    /**
     * Odpytuje trace spans.
     *
     * Endpoint:
     * GET /api/v1/phase3/traces
     *
     * Parametry:
     * - tenantId,
     * - traceId,
     * - service,
     * - start,
     * - end,
     * - limit.
     *
     * Użycie:
     * - znalezienie wszystkich spanów dla konkretnego traceId,
     * - analiza trace'ów konkretnego serwisu,
     * - wsparcie correlation API.
     *
     * Repository odpowiada za fizyczny odczyt z tabeli trace_spans.
     */
    @GetMapping("/traces")
    public Object traces(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) Instant start,
            @RequestParam(required = false) Instant end,
            @RequestParam(defaultValue = "100") int limit
    ) {
        Rbac.requireRead(tenantId);

        return repository.queryTraceSpans(
                tenantId,
                traceId,
                service,
                start,
                end,
                limit
        );
    }
}