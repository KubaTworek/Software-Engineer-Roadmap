package com.example.observability.server.repository;

import com.example.observability.server.alert.AlertEvent;
import com.example.observability.server.alert.AlertRule;
import com.example.observability.server.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class TelemetryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final int defaultLimit;
    private final int maxLimit;

    public TelemetryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper,
                               @Value("${telemetry.query.default-limit}") int defaultLimit,
                               @Value("${telemetry.query.max-limit}") int maxLimit) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
    }

    public void insertLogs(LogIngestRequest request) {
        String sql = "INSERT INTO logs (tenant_id, timestamp, level, service, host, trace_id, message, attributes_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbc.batchUpdate(sql, request.getLogs(), Math.max(1, request.getLogs().size()), (ps, log) -> {
            ps.setString(1, request.getTenantId());
            ps.setTimestamp(2, Timestamp.from(log.getTimestamp()));
            ps.setString(3, safe(log.getLevel()));
            ps.setString(4, safe(log.getService()));
            ps.setString(5, safe(log.getHost()));
            ps.setString(6, safe(log.getTraceId()));
            ps.setString(7, safe(log.getMessage()));
            ps.setString(8, toJson(log.getAttributes()));
        });
    }

    public void insertMetrics(MetricIngestRequest request) {
        List<Object[]> rows = new ArrayList<>();
        for (MetricSeriesDto series : request.getSeries()) {
            String labelsJson = toJson(series.getLabels());
            for (MetricSampleDto sample : series.getSamples()) {
                rows.add(new Object[]{request.getTenantId(), series.getName(), Timestamp.from(sample.getTimestamp()), sample.getValue(), labelsJson});
            }
        }
        jdbc.batchUpdate("INSERT INTO metrics_samples (tenant_id, metric_name, timestamp, value, labels_json) VALUES (?, ?, ?, ?, ?)", rows);
    }

    public List<LogQueryResult> queryLogs(String tenantId, String service, String level, String contains,
                                          Instant start, Instant end, Integer requestedLimit) {
        int limit = limit(requestedLimit);
        StringBuilder sql = new StringBuilder("SELECT tenant_id, timestamp, level, service, host, trace_id, message, attributes_json FROM logs WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (service != null && !service.isBlank()) { sql.append(" AND service = ?"); args.add(service); }
        if (level != null && !level.isBlank()) { sql.append(" AND level = ?"); args.add(level.toUpperCase()); }
        if (contains != null && !contains.isBlank()) { sql.append(" AND positionCaseInsensitive(message, ?) > 0"); args.add(contains); }
        if (start != null) { sql.append(" AND timestamp >= ?"); args.add(Timestamp.from(start)); }
        if (end != null) { sql.append(" AND timestamp <= ?"); args.add(Timestamp.from(end)); }
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

    public List<MetricPoint> queryMetricSeries(String tenantId, String metricName, String service,
                                               Instant start, Instant end, int stepSeconds) {
        StringBuilder sql = new StringBuilder("""
                SELECT toStartOfInterval(timestamp, INTERVAL ? SECOND) AS bucket, avg(value) AS value
                FROM metrics_samples
                WHERE tenant_id = ? AND metric_name = ? AND timestamp >= ? AND timestamp <= ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(stepSeconds);
        args.add(tenantId);
        args.add(metricName);
        args.add(Timestamp.from(start));
        args.add(Timestamp.from(end));
        if (service != null && !service.isBlank()) {
            sql.append(" AND service = ?");
            args.add(service);
        }
        sql.append(" GROUP BY bucket ORDER BY bucket ASC");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new MetricPoint(
                rs.getTimestamp("bucket").toInstant(), rs.getDouble("value")
        ), args.toArray());
    }

    public double evaluateMetric(AlertRule rule, Instant start, Instant end) {
        String aggregation = switch (rule.getAggregation().toLowerCase()) {
            case "avg" -> "avg";
            case "max" -> "max";
            case "min" -> "min";
            case "count" -> "count";
            default -> "sum";
        };
        StringBuilder sql = new StringBuilder("SELECT " + aggregation + "(value) AS observed FROM metrics_samples WHERE tenant_id=? AND metric_name=? AND timestamp>=? AND timestamp<=?");
        List<Object> args = new ArrayList<>();
        args.add(rule.getTenantId());
        args.add(rule.getMetricName());
        args.add(Timestamp.from(start));
        args.add(Timestamp.from(end));
        for (Map.Entry<String, String> entry : rule.getLabelFilters().entrySet()) {
            sql.append(" AND JSONExtractString(labels_json, ?) = ?");
            args.add(entry.getKey());
            args.add(entry.getValue());
        }
        Double value = jdbc.queryForObject(sql.toString(), Double.class, args.toArray());
        return value == null ? 0.0 : value;
    }

    public void insertAlertEvent(AlertEvent event) {
        jdbc.update("INSERT INTO alert_events (tenant_id, rule_id, rule_name, status, evaluated_at, observed_value, threshold, message) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                event.tenantId(), event.ruleId(), event.ruleName(), event.status(), Timestamp.from(event.evaluatedAt()),
                event.observedValue(), event.threshold(), event.message());
    }

    public List<AlertEvent> queryAlertEvents(String tenantId, Integer requestedLimit) {
        int limit = limit(requestedLimit);
        return jdbc.query("SELECT tenant_id, rule_id, rule_name, status, evaluated_at, observed_value, threshold, message FROM alert_events WHERE tenant_id = ? ORDER BY evaluated_at DESC LIMIT " + limit,
                (rs, rowNum) -> new AlertEvent(
                        rs.getString("tenant_id"),
                        rs.getString("rule_id"),
                        rs.getString("rule_name"),
                        rs.getString("status"),
                        rs.getTimestamp("evaluated_at").toInstant(),
                        rs.getDouble("observed_value"),
                        rs.getDouble("threshold"),
                        rs.getString("message")
                ), tenantId);
    }

    private int limit(Integer requested) {
        if (requested == null) return defaultLimit;
        return Math.max(1, Math.min(maxLimit, requested));
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception e) { return "{}"; }
    }

    private Map<String, Object> fromJsonMap(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private String safe(String value) { return value == null ? "" : value; }
}
