package com.example.observability.server.controller;

import com.example.observability.server.auth.Rbac;
import com.example.observability.server.cardinality.CardinalityGuard;
import com.example.observability.server.model.IngestResponse;
import com.example.observability.server.model.LogIngestRequest;
import com.example.observability.server.model.MetricIngestRequest;
import com.example.observability.server.model.TraceIngestRequest;
import com.example.observability.server.quota.QuotaService;
import com.example.observability.server.repository.TelemetryRepository;
import com.example.observability.server.util.Validation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Główne API ingestu telemetrycznego.
 *
 * Ten controller przyjmuje dane od agentów, aplikacji i integracji:
 * - logi,
 * - metryki,
 * - trace spans.
 *
 * To jest warstwa wejściowa systemu observability.
 * Jej główne obowiązki:
 *
 * 1. Ustalenie tenantId.
 * 2. Sprawdzenie uprawnień zapisu przez RBAC.
 * 3. Sprawdzenie quota, żeby tenant nie przeciążył systemu.
 * 4. Normalizacja brakujących pól.
 * 5. Kontrola kardynalności metryk.
 * 6. Przekazanie danych dalej do pipeline'u.
 *
 * Logi i metryki trafiają do Kafki.
 * Trace'y w tej wersji są zapisywane bezpośrednio do repozytorium.
 */
@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {

    /**
     * Kafka producer używany do asynchronicznego przekazania logów i metryk
     * do dalszych pipeline'ów przetwarzania.
     *
     * Controller nie zapisuje logów/metryk bezpośrednio do ClickHouse.
     * Dzięki temu ingest jest odporniejszy na chwilowe spowolnienia storage'u.
     */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Serializuje requesty ingestu do JSON-a przed wysłaniem do Kafki.
     *
     * Kafka przechowuje payload jako String,
     * a consumer po drugiej stronie deserializuje go z powrotem do modelu.
     */
    private final ObjectMapper objectMapper;

    /**
     * Nazwa topicu Kafki dla logów.
     *
     * Wartość pochodzi z konfiguracji:
     * telemetry.kafka.logs-topic
     */
    private final String logsTopic;

    /**
     * Nazwa topicu Kafki dla metryk.
     *
     * Wartość pochodzi z konfiguracji:
     * telemetry.kafka.metrics-topic
     */
    private final String metricsTopic;

    /**
     * Serwis pilnujący limitów ingestu.
     *
     * Chroni system przed sytuacją, w której jeden tenant wysyła zbyt dużo:
     * - logów,
     * - próbek metryk,
     * - potencjalnie kosztownych danych.
     */
    private final QuotaService quotaService;

    /**
     * Mechanizm ochrony przed eksplozją kardynalności metryk.
     *
     * To bardzo ważne dla stabilności systemu.
     * Metryki z labelami typu userId, requestId, sessionId mogą wygenerować
     * miliony unikalnych serii i zabić storage/query engine.
     */
    private final CardinalityGuard cardinalityGuard;

    /**
     * Repozytorium telemetryczne.
     *
     * W tej klasie używane głównie do zapisu trace spans.
     * Logi i metryki idą przez Kafkę, ale trace'y w tej wersji trafiają
     * bezpośrednio do warstwy danych.
     */
    private final TelemetryRepository repository;

    public IngestController(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${telemetry.kafka.logs-topic}") String logsTopic,
            @Value("${telemetry.kafka.metrics-topic}") String metricsTopic,
            QuotaService quotaService,
            CardinalityGuard cardinalityGuard,
            TelemetryRepository repository
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.logsTopic = logsTopic;
        this.metricsTopic = metricsTopic;
        this.quotaService = quotaService;
        this.cardinalityGuard = cardinalityGuard;
        this.repository = repository;
    }

    /**
     * Przyjmuje batch logów.
     *
     * Endpoint:
     * POST /api/v1/ingest/logs
     *
     * Przepływ:
     * 1. Ustawia tenantId, jeśli go brakuje.
     * 2. Sprawdza, czy caller ma prawo pisać dane dla tego tenanta.
     * 3. Sprawdza quota na liczbę logów.
     * 4. Normalizuje wymagane pola logów.
     * 5. Serializuje request do JSON.
     * 6. Wysyła payload do topicu Kafki dla logów.
     * 7. Zwraca HTTP 202 Accepted.
     *
     * Kafka key = tenantId.
     * Dzięki temu dane tego samego tenanta mogą trafiać spójnie do partycji
     * i łatwiej je przetwarzać tenant-aware.
     */
    @PostMapping("/logs")
    public CompletableFuture<ResponseEntity<IngestResponse>> ingestLogs(
            @RequestBody LogIngestRequest request
    ) throws JsonProcessingException {

        // Fallback "demo" jest wygodny lokalnie, ale produkcyjnie tenantId powinien być jawny albo wynikać z API key.
        request.setTenantId(Validation.required(request.getTenantId(), "demo"));

        // Tylko writer/admin danego tenanta może wysyłać logi.
        Rbac.requireWrite(request.getTenantId());

        // Quota chroni backend przed zbyt dużymi batchami albo nadmiernym ruchem danego tenanta.
        quotaService.checkLogs(
                request.getTenantId(),
                request.getLogs() == null ? 0 : request.getLogs().size()
        );

        /*
         * Normalizacja każdego log eventu.
         *
         * Backend wymaga spójnych pól, więc uzupełniamy:
         * - timestamp,
         * - level,
         * - service,
         * - host.
         *
         * Dzięki temu późniejszy indexer/query engine nie musi obsługiwać tylu nulli.
         */
        request.getLogs().forEach(log -> {
            if (log.getTimestamp() == null) {
                log.setTimestamp(Instant.now());
            }

            log.setLevel(
                    Validation.required(log.getLevel(), "INFO").toUpperCase()
            );

            log.setService(
                    Validation.required(log.getService(), "unknown")
            );

            log.setHost(
                    Validation.required(log.getHost(), "unknown")
            );
        });

        String payload = objectMapper.writeValueAsString(request);

        /*
         * Wysyłka do Kafki jest asynchroniczna.
         *
         * Controller zwraca 202 dopiero po potwierdzeniu send() przez KafkaTemplate.
         * To oznacza: "dane zostały przyjęte do pipeline'u",
         * a niekoniecznie "dane są już zapisane w ClickHouse".
         */
        return kafkaTemplate
                .send(logsTopic, request.getTenantId(), payload)
                .thenApply(result -> ResponseEntity.accepted().body(
                        new IngestResponse(
                                "accepted",
                                request.getLogs().size(),
                                logsTopic
                        )
                ));
    }

    /**
     * Przyjmuje batch metryk.
     *
     * Endpoint:
     * POST /api/v1/ingest/metrics
     *
     * Przepływ:
     * 1. Ustawia tenantId.
     * 2. Sprawdza uprawnienie write.
     * 3. Uzupełnia brakujące timestampy próbek.
     * 4. Liczy łączną liczbę samples.
     * 5. Sprawdza quota na samples.
     * 6. Uruchamia cardinality guard.
     * 7. Wysyła request do topicu Kafki dla metryk.
     *
     * Najważniejsza różnica względem logów:
     * metryki przechodzą przez CardinalityGuard.
     */
    @PostMapping("/metrics")
    public CompletableFuture<ResponseEntity<IngestResponse>> ingestMetrics(
            @RequestBody MetricIngestRequest request
    ) throws JsonProcessingException {

        request.setTenantId(Validation.required(request.getTenantId(), "demo"));

        // Tylko writer/admin danego tenanta może wysyłać metryki.
        Rbac.requireWrite(request.getTenantId());

        /*
         * Uzupełniamy timestampy próbek.
         *
         * Jeśli klient nie poda timestampu, backend traktuje próbkę
         * jako zmierzoną w momencie ingestu.
         */
        request.getSeries().forEach(series ->
                series.getSamples().forEach(sample -> {
                    if (sample.getTimestamp() == null) {
                        sample.setTimestamp(Instant.now());
                    }
                })
        );

        // Liczba samples jest podstawą rozliczania quota dla metryk.
        int samples = request.getSeries()
                .stream()
                .mapToInt(s -> s.getSamples().size())
                .sum();

        quotaService.checkMetricSamples(request.getTenantId(), samples);

        /*
         * Krytyczna kontrola dla metryk.
         *
         * Guard może:
         * - odrzucić zabronione labele,
         * - wykryć za dużo unikalnych serii,
         * - zapisać informację o nowej serii,
         * - chronić storage przed high-cardinality explosion.
         */
        cardinalityGuard.validateAndRecord(request);

        String payload = objectMapper.writeValueAsString(request);

        return kafkaTemplate
                .send(metricsTopic, request.getTenantId(), payload)
                .thenApply(result -> ResponseEntity.accepted().body(
                        new IngestResponse(
                                "accepted",
                                samples,
                                metricsTopic
                        )
                ));
    }

    /**
     * Przyjmuje batch trace spanów.
     *
     * Endpoint:
     * POST /api/v1/ingest/traces
     *
     * Trace'y służą do korelacji:
     * - logów,
     * - metryk,
     * - operacji/requestów w aplikacji.
     *
     * W przeciwieństwie do logów i metryk, w tej implementacji trace'y
     * są zapisywane bezpośrednio przez TelemetryRepository,
     * bez przechodzenia przez Kafkę.
     *
     * To jest prostsze dla MVP/Fazy 3, ale produkcyjnie trace ingest
     * też powinien zwykle iść przez kolejkę.
     */
    @PostMapping("/traces")
    public ResponseEntity<IngestResponse> ingestTraces(
            @RequestBody TraceIngestRequest request
    ) {
        request.setTenantId(Validation.required(request.getTenantId(), "demo"));

        // Tylko writer/admin danego tenanta może wysyłać trace'y.
        Rbac.requireWrite(request.getTenantId());

        // Null-safe fallback, żeby dalsza logika nie wywaliła się na pustym spans.
        if (request.getSpans() == null) {
            request.setSpans(java.util.List.of());
        }

        /*
         * Normalizacja spanów.
         *
         * Uzupełniamy minimalny zestaw pól potrzebny do korelacji i query:
         * - startTime,
         * - endTime,
         * - service,
         * - operation,
         * - status.
         */
        request.getSpans().forEach(span -> {
            Instant now = Instant.now();

            if (span.getStartTime() == null) {
                span.setStartTime(now);
            }

            /*
             * Jeśli klient nie poda endTime, wyliczamy go ze startTime + durationMs.
             * Math.max(1, durationMs) zabezpiecza przed zerowym/ujemnym czasem trwania.
             */
            if (span.getEndTime() == null) {
                span.setEndTime(
                        span.getStartTime().plusMillis(
                                (long) Math.max(1, span.getDurationMs())
                        )
                );
            }

            span.setService(
                    Validation.required(span.getService(), "unknown")
            );

            span.setOperation(
                    Validation.required(span.getOperation(), "unknown")
            );

            span.setStatus(
                    Validation.required(span.getStatus(), "OK").toUpperCase()
            );
        });

        /*
         * Bezpośredni zapis trace'ów do repozytorium.
         *
         * To pozwala później robić correlation API po traceId:
         * - pokaż logi dla traceId,
         * - pokaż spany dla traceId,
         * - połącz spike metryki z błędami i spanami.
         */
        repository.insertTraces(request);

        return ResponseEntity.accepted().body(
                new IngestResponse(
                        "accepted",
                        request.getSpans().size(),
                        "trace_spans"
                )
        );
    }
}