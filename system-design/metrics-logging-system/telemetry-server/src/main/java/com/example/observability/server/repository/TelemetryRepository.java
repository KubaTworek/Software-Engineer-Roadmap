package com.example.observability.server.repository;

import com.example.observability.server.alert.AlertEvent;
import com.example.observability.server.alert.AlertRule;
import com.example.observability.server.model.*;
import com.example.observability.server.cardinality.CardinalityGuard;
import com.example.observability.server.fulltext.FullTextIndexService;
import com.example.observability.server.phase3.AnomalyDetector;
import com.example.observability.server.region.MultiRegionService;
import com.example.observability.server.tenant.TenantModels;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Główna warstwa dostępu do danych telemetrycznych.
 *
 * Ta klasa jest centralnym repozytorium aplikacji.
 * Odpowiada za fizyczny zapis i odczyt danych z bazy przez JdbcTemplate.
 *
 * Obsługiwane obszary:
 * - logi,
 * - metryki raw i rollupy,
 * - alert events,
 * - trace spans,
 * - cardinality registry,
 * - full-text index,
 * - anomaly events,
 * - tenant management,
 * - API keys,
 * - replication events.
 *
 * Controller i serwisy nie powinny znać szczegółów SQL.
 * To repozytorium ukrywa strukturę tabel i mapowanie rekordów na modele domenowe.
 */
@Repository
public class TelemetryRepository {

    /**
     * Springowy wrapper na JDBC.
     *
     * Tutaj wykonywane są wszystkie zapytania SQL:
     * - INSERT,
     * - SELECT,
     * - batchUpdate.
     *
     * W projekcie działa jako adapter do ClickHouse.
     */
    private final JdbcTemplate jdbc;

    /**
     * Mapper JSON używany do serializacji i deserializacji pól JSON-owych.
     *
     * W bazie część danych jest trzymana jako String JSON:
     * - log attributes,
     * - metric labels,
     * - trace attributes.
     */
    private final ObjectMapper objectMapper;

    /**
     * Domyślny limit wyników, gdy klient nie poda limitu.
     *
     * Chroni query API przed przypadkowym zwróceniem zbyt dużej liczby rekordów.
     */
    private final int defaultLimit;

    /**
     * Maksymalny limit wyników wymuszany po stronie backendu.
     *
     * Nawet jeśli klient poda bardzo duży limit, zostanie on przycięty.
     */
    private final int maxLimit;

    public TelemetryRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${telemetry.query.default-limit}") int defaultLimit,
            @Value("${telemetry.query.max-limit}") int maxLimit
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
    }

    /**
     * Zapisuje batch logów do tabeli logs.
     *
     * Wywoływane zwykle przez consumer pipeline'u logów,
     * po tym jak IngestController wrzuci payload do Kafki.
     *
     * Każdy log jest zapisywany z tenantId requestu.
     * To tenantId jest podstawowym warunkiem izolacji danych.
     */
    public void insertLogs(LogIngestRequest request) {
        String sql = """
                INSERT INTO logs
                (tenant_id, timestamp, level, service, host, trace_id, message, attributes_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        /*
         * batchUpdate jest ważne wydajnościowo.
         *
         * Logi przychodzą w batchach, więc nie robimy osobnego INSERT-a
         * dla każdej linii loga.
         */
        jdbc.batchUpdate(sql, request.getLogs(), Math.max(1, request.getLogs().size()), (ps, log) -> {
            ps.setString(1, request.getTenantId());
            ps.setTimestamp(2, Timestamp.from(log.getTimestamp()));
            ps.setString(3, safe(log.getLevel()).toUpperCase());
            ps.setString(4, safe(log.getService()));
            ps.setString(5, safe(log.getHost()));
            ps.setString(6, safe(log.getTraceId()));
            ps.setString(7, safe(log.getMessage()));

            // Atrybuty loga są zapisywane jako JSON string.
            ps.setString(8, toJson(log.getAttributes()));
        });
    }

    /**
     * Zapisuje próbki metryk do tabeli metrics_samples.
     *
     * Model danych:
     * - jedna seria metryczna ma nazwę i labels,
     * - seria zawiera wiele próbek,
     * - każda próbka staje się osobnym rekordem w bazie.
     */
    public void insertMetrics(MetricIngestRequest request) {
        List<Object[]> rows = new ArrayList<>();

        for (MetricSeriesDto series : request.getSeries()) {
            String labelsJson = toJson(series.getLabels());

            for (MetricSampleDto sample : series.getSamples()) {
                rows.add(new Object[]{
                        request.getTenantId(),
                        series.getName(),
                        Timestamp.from(sample.getTimestamp()),
                        sample.getValue(),
                        labelsJson
                });
            }
        }

        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO metrics_samples
                    (tenant_id, metric_name, timestamp, value, labels_json)
                    VALUES (?, ?, ?, ?, ?)
                    """, rows);
        }
    }

    /**
     * Odpytuje logi z podstawowymi filtrami.
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
     * contains używa positionCaseInsensitive, czyli prostego wyszukiwania
     * po treści message. To nie jest pełny full-text engine.
     */
    public List<LogQueryResult> queryLogs(
            String tenantId,
            String service,
            String level,
            String contains,
            Instant start,
            Instant end,
            Integer requestedLimit
    ) {
        int limit = limit(requestedLimit);

        StringBuilder sql = new StringBuilder("""
                SELECT tenant_id, timestamp, level, service, host, trace_id, message, attributes_json
                FROM logs
                WHERE tenant_id = ?
                """);

        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        if (service != null && !service.isBlank()) {
            sql.append(" AND service = ?");
            args.add(service);
        }

        if (level != null && !level.isBlank()) {
            sql.append(" AND level = ?");
            args.add(level.toUpperCase());
        }

        if (contains != null && !contains.isBlank()) {
            sql.append(" AND positionCaseInsensitive(message, ?) > 0");
            args.add(contains);
        }

        if (start != null) {
            sql.append(" AND timestamp >= ?");
            args.add(Timestamp.from(start));
        }

        if (end != null) {
            sql.append(" AND timestamp <= ?");
            args.add(Timestamp.from(end));
        }

        /*
         * LIMIT jest doklejany po clampie w limit().
         * Dzięki temu nie ma ryzyka, że użytkownik wstrzyknie SQL przez limit.
         */
        sql.append(" ORDER BY timestamp DESC LIMIT ").append(limit);

        return jdbc.query(sql.toString(), (rs, rowNum) -> new LogQueryResult(
                rs.getString("tenant_id"),
                rs.getTimestamp("timestamp").toInstant(),
                rs.getString("level"),
                rs.getString("service"),
                rs.getString("host"),
                rs.getString("trace_id"),
                rs.getString("message"),
                fromJsonMap(rs.getString("attributes_json"))
        ), args.toArray());
    }

    /**
     * Odpytuje metrykę z domyślnej tabeli raw samples.
     *
     * To wygodny overload używany, gdy caller nie wybiera sam tabeli.
     */
    public List<MetricPoint> queryMetricSeries(
            String tenantId,
            String metricName,
            String service,
            Instant start,
            Instant end,
            int stepSeconds
    ) {
        return queryMetricSeries(
                "metrics_samples",
                tenantId,
                metricName,
                service,
                start,
                end,
                stepSeconds
        );
    }

    /**
     * Odpytuje serię metryczną z raw table albo rollup table.
     *
     * table może wskazywać:
     * - metrics_samples,
     * - metrics_rollup_1m,
     * - metrics_rollup_5m,
     * - metrics_rollup_1h.
     *
     * To jest miejsce, gdzie QueryPlanner materialnie wpływa na storage:
     * planner wybiera tabelę, a repository wykonuje query.
     */
    public List<MetricPoint> queryMetricSeries(
            String table,
            String tenantId,
            String metricName,
            String service,
            Instant start,
            Instant end,
            int stepSeconds
    ) {
        /*
         * Whitelist nazw tabel.
         *
         * Bardzo ważne, bo nazwy tabel nie da się bindować jako parametr SQL.
         * Bez whitelisty byłoby ryzyko SQL injection przez table.
         */
        String safeTable = switch (table) {
            case "metrics_rollup_1m", "metrics_rollup_5m", "metrics_rollup_1h" -> table;
            default -> "metrics_samples";
        };

        boolean rollup = !"metrics_samples".equals(safeTable);
        String timeColumn = rollup ? "bucket_start" : "timestamp";
        String valueExpr = rollup ? "avg_value" : "value";

        StringBuilder sql = new StringBuilder("""
                SELECT toStartOfInterval(%s, INTERVAL ? SECOND) AS bucket, avg(%s) AS value
                FROM %s
                WHERE tenant_id = ? AND metric_name = ? AND %s >= ? AND %s <= ?
                """.formatted(timeColumn, valueExpr, safeTable, timeColumn, timeColumn));

        List<Object> args = new ArrayList<>();
        args.add(stepSeconds);
        args.add(tenantId);
        args.add(metricName);
        args.add(Timestamp.from(start));
        args.add(Timestamp.from(end));

        /*
         * service jest filtrowany z labels_json.
         *
         * To jest wygodne dla MVP, ale przy dużej skali warto rozważyć
         * materializowaną kolumnę service dla szybszych query.
         */
        if (service != null && !service.isBlank()) {
            sql.append(" AND JSONExtractString(labels_json, 'service') = ?");
            args.add(service);
        }

        sql.append(" GROUP BY bucket ORDER BY bucket ASC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new MetricPoint(
                rs.getTimestamp("bucket").toInstant(),
                rs.getDouble("value")
        ), args.toArray());
    }

    /**
     * Liczy jedną wartość metryki dla reguły alertowej.
     *
     * AlertEvaluator używa tej metody, żeby dostać observed value
     * dla danego okna czasu.
     *
     * Reguła definiuje:
     * - tenantId,
     * - metricName,
     * - aggregation,
     * - label filters.
     */
    public double evaluateMetric(AlertRule rule, Instant start, Instant end) {
        String aggregation = switch (rule.getAggregation().toLowerCase()) {
            case "avg" -> "avg";
            case "max" -> "max";
            case "min" -> "min";
            case "count" -> "count";
            default -> "sum";
        };

        StringBuilder sql = new StringBuilder("""
                SELECT %s(value) AS observed
                FROM metrics_samples
                WHERE tenant_id=? AND metric_name=? AND timestamp>=? AND timestamp<=?
                """.formatted(aggregation));

        List<Object> args = new ArrayList<>();
        args.add(rule.getTenantId());
        args.add(rule.getMetricName());
        args.add(Timestamp.from(start));
        args.add(Timestamp.from(end));

        /*
         * Filtry po labelach z reguły alertowej.
         *
         * Przykład:
         * service=payments
         * status=500
         */
        for (Map.Entry<String, String> entry : rule.getLabelFilters().entrySet()) {
            sql.append(" AND JSONExtractString(labels_json, ?) = ?");
            args.add(entry.getKey());
            args.add(entry.getValue());
        }

        Double value = jdbc.queryForObject(sql.toString(), Double.class, args.toArray());
        return value == null ? 0.0 : value;
    }

    /**
     * Zapisuje event alertowy do historii alertów.
     *
     * Wywoływane przez AlertEvaluator po wykryciu stanu FIRING.
     */
    public void insertAlertEvent(AlertEvent event) {
        jdbc.update("""
                INSERT INTO alert_events
                (tenant_id, rule_id, rule_name, status, evaluated_at, observed_value, threshold, message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.tenantId(),
                event.ruleId(),
                event.ruleName(),
                event.status(),
                Timestamp.from(event.evaluatedAt()),
                event.observedValue(),
                event.threshold(),
                event.message()
        );
    }

    /**
     * Pobiera historię alertów dla tenanta.
     *
     * Używane przez AlertController do endpointu /api/v1/alerts/events.
     */
    public List<AlertEvent> queryAlertEvents(String tenantId, Integer requestedLimit) {
        int limit = limit(requestedLimit);

        return jdbc.query("""
                SELECT tenant_id, rule_id, rule_name, status, evaluated_at, observed_value, threshold, message
                FROM alert_events
                WHERE tenant_id = ?
                ORDER BY evaluated_at DESC
                LIMIT """ + limit,
                (rs, rowNum) -> new AlertEvent(
                        rs.getString("tenant_id"),
                        rs.getString("rule_id"),
                        rs.getString("rule_name"),
                        rs.getString("status"),
                        rs.getTimestamp("evaluated_at").toInstant(),
                        rs.getDouble("observed_value"),
                        rs.getDouble("threshold"),
                        rs.getString("message")
                ),
                tenantId
        );
    }

    /**
     * Zapisuje trace spans.
     *
     * Trace'y są podstawą korelacji:
     * - traceId -> spany,
     * - traceId -> logi,
     * - service latency -> trace details.
     */
    public void insertTraces(TraceIngestRequest request) {
        String sql = """
                INSERT INTO trace_spans
                (tenant_id, trace_id, span_id, parent_span_id, service, operation,
                 start_time, end_time, duration_ms, status, attributes_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbc.batchUpdate(sql, request.getSpans(), Math.max(1, request.getSpans().size()), (ps, span) -> {
            Instant start = span.getStartTime() == null ? Instant.now() : span.getStartTime();
            Instant end = span.getEndTime() == null
                    ? start.plusMillis((long) span.getDurationMs())
                    : span.getEndTime();

            double duration = span.getDurationMs() > 0
                    ? span.getDurationMs()
                    : Math.max(0, end.toEpochMilli() - start.toEpochMilli());

            ps.setString(1, request.getTenantId());
            ps.setString(2, safe(span.getTraceId()));
            ps.setString(3, safe(span.getSpanId()));
            ps.setString(4, safe(span.getParentSpanId()));
            ps.setString(5, safe(span.getService()));
            ps.setString(6, safe(span.getOperation()));
            ps.setTimestamp(7, Timestamp.from(start));
            ps.setTimestamp(8, Timestamp.from(end));
            ps.setDouble(9, duration);
            ps.setString(10, safe(span.getStatus()).toUpperCase());
            ps.setString(11, toJson(span.getAttributes()));
        });
    }

    /**
     * Odpytuje trace spans po tenantId oraz opcjonalnie:
     * - traceId,
     * - service,
     * - start,
     * - end.
     *
     * Używane przez Phase3Controller i correlation service.
     */
    public List<TraceSpanResult> queryTraceSpans(
            String tenantId,
            String traceId,
            String service,
            Instant start,
            Instant end,
            Integer requestedLimit
    ) {
        int limit = limit(requestedLimit);

        StringBuilder sql = new StringBuilder("""
                SELECT tenant_id, trace_id, span_id, parent_span_id, service, operation,
                       start_time, end_time, duration_ms, status, attributes_json
                FROM trace_spans
                WHERE tenant_id = ?
                """);

        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        if (traceId != null && !traceId.isBlank()) {
            sql.append(" AND trace_id = ?");
            args.add(traceId);
        }

        if (service != null && !service.isBlank()) {
            sql.append(" AND service = ?");
            args.add(service);
        }

        if (start != null) {
            sql.append(" AND start_time >= ?");
            args.add(Timestamp.from(start));
        }

        if (end != null) {
            sql.append(" AND start_time <= ?");
            args.add(Timestamp.from(end));
        }

        sql.append(" ORDER BY start_time ASC LIMIT ").append(limit);

        return jdbc.query(sql.toString(), (rs, rowNum) -> new TraceSpanResult(
                rs.getString("tenant_id"),
                rs.getString("trace_id"),
                rs.getString("span_id"),
                rs.getString("parent_span_id"),
                rs.getString("service"),
                rs.getString("operation"),
                rs.getTimestamp("start_time").toInstant(),
                rs.getTimestamp("end_time").toInstant(),
                rs.getDouble("duration_ms"),
                rs.getString("status"),
                fromJsonMap(rs.getString("attributes_json"))
        ), args.toArray());
    }

    /**
     * Pobiera logi powiązane z konkretnym traceId.
     *
     * To jest kluczowe dla korelacji logs <-> traces.
     */
    public List<LogQueryResult> queryLogsByTraceId(
            String tenantId,
            String traceId,
            Integer requestedLimit
    ) {
        int limit = limit(requestedLimit);

        return jdbc.query("""
                SELECT tenant_id, timestamp, level, service, host, trace_id, message, attributes_json
                FROM logs
                WHERE tenant_id = ? AND trace_id = ?
                ORDER BY timestamp ASC
                LIMIT """ + limit,
                (rs, rowNum) -> new LogQueryResult(
                        rs.getString("tenant_id"),
                        rs.getTimestamp("timestamp").toInstant(),
                        rs.getString("level"),
                        rs.getString("service"),
                        rs.getString("host"),
                        rs.getString("trace_id"),
                        rs.getString("message"),
                        fromJsonMap(rs.getString("attributes_json"))
                ),
                tenantId,
                traceId
        );
    }

    /**
     * Rejestruje kardynalność metryk.
     *
     * Dla każdej serii:
     * - zapisuje hash zestawu labeli,
     * - zapisuje wartości labeli per godzina.
     *
     * To pozwala potem wygenerować raport:
     * - ile unikalnych serii istnieje,
     * - które labele mają najwięcej unikalnych wartości,
     * - które metryki są ryzykowne kosztowo.
     */
    public void recordMetricCardinality(MetricIngestRequest request, Instant bucketStart) {
        for (MetricSeriesDto series : request.getSeries()) {
            String labelsJson = toJson(series.getLabels());
            String labelsHash = sha256(labelsJson);

            jdbc.update("""
                    INSERT INTO metric_series_registry
                    (tenant_id, metric_name, labels_hash, labels_json, first_seen, last_seen, status, reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    request.getTenantId(),
                    series.getName(),
                    labelsHash,
                    labelsJson,
                    Timestamp.from(bucketStart),
                    Timestamp.from(Instant.now()),
                    "active",
                    "accepted"
            );

            for (Map.Entry<String, String> e : series.getLabels().entrySet()) {
                String value = e.getValue() == null ? "" : e.getValue();

                jdbc.update("""
                        INSERT INTO metric_cardinality_hourly
                        (tenant_id, metric_name, label_key, label_value_hash, bucket_start, examples)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        request.getTenantId(),
                        series.getName(),
                        e.getKey(),
                        sha256(value),
                        Timestamp.from(bucketStart),
                        new String[]{
                                value.length() > 80 ? value.substring(0, 80) : value
                        }
                );
            }
        }
    }

    /**
     * Buduje raport kardynalności dla konkretnej metryki.
     *
     * Raport pokazuje:
     * - liczbę unikalnych serii,
     * - labele o największej liczbie unikalnych wartości,
     * - prostą ocenę ryzyka: low / medium / high.
     */
    public CardinalityGuard.CardinalityReport cardinalityReport(
            String tenantId,
            String metricName,
            int hours
    ) {
        Instant start = Instant.now().minusSeconds(Math.max(1, hours) * 3600L);

        Long series = jdbc.queryForObject("""
                SELECT uniqExact(labels_hash)
                FROM metric_series_registry
                WHERE tenant_id=? AND metric_name=? AND last_seen >= ?
                """,
                Long.class,
                tenantId,
                metricName,
                Timestamp.from(start)
        );

        List<CardinalityGuard.LabelStats> labels = jdbc.query("""
                SELECT label_key,
                       uniqExact(label_value_hash) AS values,
                       groupArrayDistinct(arrayElement(examples, 1)) AS examples
                FROM metric_cardinality_hourly
                WHERE tenant_id=? AND metric_name=? AND bucket_start >= ?
                GROUP BY label_key
                ORDER BY values DESC
                LIMIT 50
                """,
                (rs, rowNum) -> {
                    long values = rs.getLong("values");
                    String risk = values > 10000 ? "high" : values > 1000 ? "medium" : "low";

                    return new CardinalityGuard.LabelStats(
                            rs.getString("label_key"),
                            values,
                            List.of(rs.getString("examples")),
                            risk
                    );
                },
                tenantId,
                metricName,
                Timestamp.from(start)
        );

        return new CardinalityGuard.CardinalityReport(
                tenantId,
                metricName,
                hours,
                series == null ? 0 : series,
                labels
        );
    }

    /**
     * Zapisuje tokeny full-text indexu dla logów.
     *
     * FullTextIndexService przygotowuje agregaty termów,
     * a repozytorium zapisuje je do tabeli log_fulltext_terms.
     *
     * To jest lekki, opcjonalny indeks tekstowy.
     * Nie zastępuje pełnego Elasticsearch/OpenSearch.
     */
    public void upsertFullTextTerms(
            Map<FullTextIndexService.IndexKey, FullTextIndexService.TermStats> terms
    ) {
        if (terms.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO log_fulltext_terms
                (tenant_id, service, level, bucket_start, term, doc_count, sample_trace_ids)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        List<Object[]> rows = new ArrayList<>();

        for (Map.Entry<FullTextIndexService.IndexKey, FullTextIndexService.TermStats> e : terms.entrySet()) {
            var key = e.getKey();

            rows.add(new Object[]{
                    key.tenantId(),
                    key.service(),
                    key.level(),
                    Timestamp.from(key.bucketStart()),
                    key.term(),
                    e.getValue().count(),
                    e.getValue().sampleTraceIds().toArray(new String[0])
            });
        }

        jdbc.batchUpdate(sql, rows);
    }

    /**
     * Szuka bucketów czasowych, w których występują podane termy full-text.
     *
     * Używane przez planner/full-text service do ograniczenia zakresu skanowania logów.
     */
    public List<String> lookupFullTextBuckets(
            String tenantId,
            String service,
            String level,
            List<String> terms,
            Instant start,
            Instant end
    ) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", Collections.nCopies(terms.size(), "?"));

        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT bucket_start FROM log_fulltext_terms WHERE tenant_id=? AND term IN (" + placeholders + ")"
        );

        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(terms);

        if (service != null && !service.isBlank()) {
            sql.append(" AND service=?");
            args.add(service);
        }

        if (level != null && !level.isBlank()) {
            sql.append(" AND level=?");
            args.add(level.toUpperCase());
        }

        if (start != null) {
            sql.append(" AND bucket_start >= toStartOfHour(?)");
            args.add(Timestamp.from(start));
        }

        if (end != null) {
            sql.append(" AND bucket_start <= toStartOfHour(?)");
            args.add(Timestamp.from(end));
        }

        sql.append(" ORDER BY bucket_start DESC LIMIT 1000");

        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> rs.getTimestamp("bucket_start").toInstant().toString(),
                args.toArray()
        );
    }

    /**
     * Zapisuje wykrytą anomalię.
     *
     * AnomalyDetector wykrywa wynik, a repozytorium utrwala event.
     */
    public void insertAnomalyEvent(AnomalyDetector.AnomalyResult result) {
        jdbc.update("""
                INSERT INTO anomaly_events
                (tenant_id, metric_name, service, detected_at, method, score, baseline, observed, severity, explanation)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                result.tenantId(),
                result.metricName(),
                result.service(),
                Timestamp.from(Instant.now()),
                result.method(),
                result.score(),
                result.baseline(),
                result.observed(),
                result.severity(),
                result.explanation()
        );
    }

    /**
     * Pobiera historię anomalii dla tenanta.
     */
    public List<AnomalyDetector.AnomalyResult> queryAnomalyEvents(
            String tenantId,
            Integer requestedLimit
    ) {
        int limit = limit(requestedLimit);

        return jdbc.query("""
                SELECT tenant_id, metric_name, service, method, score, baseline, observed, severity
                FROM anomaly_events
                WHERE tenant_id=?
                ORDER BY detected_at DESC
                LIMIT """ + limit,
                (rs, rowNum) -> new AnomalyDetector.AnomalyResult(
                        rs.getString("tenant_id"),
                        rs.getString("metric_name"),
                        rs.getString("service"),
                        true,
                        rs.getDouble("score"),
                        rs.getDouble("baseline"),
                        rs.getDouble("observed"),
                        rs.getString("method"),
                        rs.getString("severity")
                ),
                tenantId
        );
    }

    /**
     * Zapisuje konfigurację tenanta.
     *
     * Używane przez TenantService przy tworzeniu lub aktualizacji tenantów.
     */
    public void upsertTenant(TenantModels.Tenant tenant) {
        jdbc.update("""
                INSERT INTO tenants
                (tenant_id, display_name, status, plan, primary_region, retention_days, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tenant.tenantId(),
                tenant.displayName(),
                tenant.status(),
                tenant.plan(),
                tenant.primaryRegion(),
                tenant.retentionDays(),
                Timestamp.from(tenant.createdAt()),
                Timestamp.from(tenant.updatedAt())
        );
    }

    /**
     * Pobiera konfigurację jednego tenanta.
     *
     * Jeśli tenant nie istnieje, zwraca tenant implicit.
     * To jest wygodne w demo/MVP, ale produkcyjnie raczej powinien być 404.
     */
    public TenantModels.Tenant getTenant(String tenantId) {
        List<TenantModels.Tenant> results = jdbc.query("""
                SELECT tenant_id, display_name, status, plan, primary_region, retention_days, created_at, updated_at
                FROM tenants
                WHERE tenant_id=?
                ORDER BY updated_at DESC
                LIMIT 1
                """,
                (rs, rowNum) -> new TenantModels.Tenant(
                        rs.getString("tenant_id"),
                        rs.getString("display_name"),
                        rs.getString("status"),
                        rs.getString("plan"),
                        rs.getString("primary_region"),
                        rs.getInt("retention_days"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                tenantId
        );

        if (results.isEmpty()) {
            return new TenantModels.Tenant(
                    tenantId,
                    tenantId,
                    "implicit",
                    "dev",
                    "local",
                    30,
                    Instant.EPOCH,
                    Instant.EPOCH
            );
        }

        return results.get(0);
    }

    /**
     * Listuje tenantów platformy.
     *
     * Używane przez endpoint wymagający platform admina.
     */
    public List<TenantModels.Tenant> listTenants() {
        return jdbc.query("""
                SELECT tenant_id, display_name, status, plan, primary_region, retention_days, created_at, updated_at
                FROM tenants
                ORDER BY tenant_id
                LIMIT 1000
                """,
                (rs, rowNum) -> new TenantModels.Tenant(
                        rs.getString("tenant_id"),
                        rs.getString("display_name"),
                        rs.getString("status"),
                        rs.getString("plan"),
                        rs.getString("primary_region"),
                        rs.getInt("retention_days"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                )
        );
    }

    /**
     * Zapisuje API key dla tenanta.
     *
     * Ważne bezpieczeństwo:
     * do bazy trafia hash tokena, nie jawny token.
     *
     * Jawny token powinien być pokazany użytkownikowi tylko raz,
     * przy tworzeniu klucza.
     */
    public void insertTenantApiKey(
            String tenantId,
            String keyId,
            String token,
            String name,
            Set<String> roles,
            Instant expiresAt
    ) {
        jdbc.update("""
                INSERT INTO tenant_api_keys
                (tenant_id, key_id, token_hash, name, roles, status, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                keyId,
                sha256(token),
                name,
                roles.toArray(new String[0]),
                "active",
                Timestamp.from(Instant.now()),
                expiresAt == null ? null : Timestamp.from(expiresAt)
        );
    }

    /**
     * Listuje bezpieczny widok API keys.
     *
     * Nie zwraca sekretu tokena.
     */
    public List<TenantModels.ApiKeyView> listTenantApiKeys(String tenantId) {
        return jdbc.query("""
                SELECT tenant_id, key_id, name, roles, status, created_at, expires_at
                FROM tenant_api_keys
                WHERE tenant_id=?
                ORDER BY created_at DESC
                LIMIT 100
                """,
                (rs, rowNum) -> new TenantModels.ApiKeyView(
                        rs.getString("tenant_id"),
                        rs.getString("key_id"),
                        rs.getString("name"),

                        /*
                         * Uwaga produkcyjna:
                         * Set.of(String.valueOf(rs.getObject("roles"))) może niepoprawnie mapować tablicę ról.
                         * Lepiej jawnie obsłużyć SQL Array / ClickHouse Array(String).
                         */
                        Set.of(String.valueOf(rs.getObject("roles"))),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("expires_at") == null
                                ? null
                                : rs.getTimestamp("expires_at").toInstant()
                ),
                tenantId
        );
    }

    /**
     * Zapisuje heartbeat/status replikacji między regionami.
     *
     * Używane przez MultiRegionService.
     */
    public void insertReplicationEvent(
            String tenantId,
            String sourceRegion,
            String targetRegion,
            String streamName,
            long lagMs,
            String status,
            String details
    ) {
        jdbc.update("""
                INSERT INTO region_replication_events
                (tenant_id, source_region, target_region, stream_name, event_time, lag_ms, status, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                sourceRegion,
                targetRegion,
                streamName,
                Timestamp.from(Instant.now()),
                lagMs,
                status,
                details
        );
    }

    /**
     * Pobiera ostatnie eventy replikacji dla tenanta.
     *
     * Na tej podstawie MultiRegionService może zbudować replication health.
     */
    public List<MultiRegionService.ReplicationStream> latestReplicationEvents(String tenantId) {
        return jdbc.query("""
                SELECT source_region, target_region, stream_name, lag_ms, status, event_time, details
                FROM region_replication_events
                WHERE tenant_id=?
                ORDER BY event_time DESC
                LIMIT 50
                """,
                (rs, rowNum) -> new MultiRegionService.ReplicationStream(
                        rs.getString("source_region"),
                        rs.getString("target_region"),
                        rs.getString("stream_name"),
                        rs.getLong("lag_ms"),
                        rs.getString("status"),
                        rs.getTimestamp("event_time").toInstant(),
                        rs.getString("details")
                ),
                tenantId
        );
    }

    /**
     * Liczy SHA-256 dla wartości tekstowej.
     *
     * Używane do:
     * - hash API key tokenów,
     * - hash label values,
     * - hash zestawu labeli metryk.
     *
     * Fallback na Objects.hashCode jest tylko awaryjny.
     * Produkcyjnie błąd MessageDigest powinien raczej przerwać operację.
     */
    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");

            byte[] digest = md.digest(
                    (input == null ? "" : input)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            StringBuilder sb = new StringBuilder();

            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (Exception e) {
            return Integer.toHexString(Objects.hashCode(input));
        }
    }

    /**
     * Normalizuje limit wyników.
     *
     * Zasada:
     * - brak limitu -> defaultLimit,
     * - limit < 1 -> 1,
     * - limit > maxLimit -> maxLimit.
     */
    private int limit(Integer requested) {
        if (requested == null) {
            return defaultLimit;
        }

        return Math.max(1, Math.min(maxLimit, requested));
    }

    /**
     * Bezpiecznie serializuje obiekt do JSON.
     *
     * Jeśli serializacja się nie uda, zwraca pusty obiekt JSON.
     * Dzięki temu pojedyncze błędne attributes nie wywalają ingestu.
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Bezpiecznie parsuje JSON do Mapy.
     *
     * Jeśli JSON jest uszkodzony albo pusty, zwraca pustą mapę.
     */
    private Map<String, Object> fromJsonMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Zamienia null na pusty string.
     *
     * Używane przy zapisie do bazy, żeby uniknąć nulli w polach tekstowych.
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}