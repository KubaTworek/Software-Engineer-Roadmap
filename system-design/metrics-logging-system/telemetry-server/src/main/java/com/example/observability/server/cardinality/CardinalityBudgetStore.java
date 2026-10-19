package com.example.observability.server.cardinality;

import com.example.observability.server.model.MetricIngestRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.Map;

/**
 * Globalny, atomowy budżet kardynalności. Blokada transakcyjna PostgreSQL jest
 * współdzielona przez wszystkie instancje API, czego nie zapewniłby lokalny
 * ConcurrentHashMap ani wcześniejsze raportowanie do ClickHouse.
 */
@Repository
public class CardinalityBudgetStore {
    private final JdbcTemplate jdbc;

    public CardinalityBudgetStore(@Qualifier("metadataJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean reserve(MetricIngestRequest request, Instant hour,
                           int maxSeries, int maxValuesPerLabel) {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (var series : request.getSeries()) {
                    String lockKey = request.getTenantId() + '|' + series.getName() + '|' + hour;
                    try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                        lock.setString(1, lockKey);
                        lock.execute();
                    }
                    var labels = series.getLabels() == null ? Map.<String, String>of() : series.getLabels();
                    String seriesHash = sha256(new TreeMap<>(labels).toString());
                    try (var insert = connection.prepareStatement("""
                            INSERT INTO metric_series_admissions VALUES (?,?,?,?)
                            ON CONFLICT DO NOTHING
                            """)) {
                        insert.setString(1, request.getTenantId());
                        insert.setString(2, series.getName());
                        insert.setTimestamp(3, Timestamp.from(hour));
                        insert.setString(4, seriesHash);
                        insert.executeUpdate();
                    }
                    if (count(connection, """
                            SELECT count(*) FROM metric_series_admissions
                            WHERE tenant_id=? AND metric_name=? AND hour_bucket=?
                            """, request.getTenantId(), series.getName(), hour) > maxSeries) {
                        connection.rollback();
                        return false;
                    }
                    for (var label : labels.entrySet()) {
                        try (var insert = connection.prepareStatement("""
                                INSERT INTO metric_label_value_admissions VALUES (?,?,?,?,?)
                                ON CONFLICT DO NOTHING
                                """)) {
                            insert.setString(1, request.getTenantId());
                            insert.setString(2, series.getName());
                            insert.setTimestamp(3, Timestamp.from(hour));
                            insert.setString(4, label.getKey());
                            insert.setString(5, sha256(label.getValue()));
                            insert.executeUpdate();
                        }
                        if (countLabel(connection, request.getTenantId(), series.getName(), hour,
                                label.getKey()) > maxValuesPerLabel) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                connection.commit();
                return true;
            } catch (java.sql.SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        }));
    }

    private long count(java.sql.Connection connection, String sql, String tenant,
                       String metric, Instant hour) throws java.sql.SQLException {
        try (var query = connection.prepareStatement(sql)) {
            query.setString(1, tenant); query.setString(2, metric);
            query.setTimestamp(3, Timestamp.from(hour));
            try (var result = query.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private long countLabel(java.sql.Connection connection, String tenant, String metric,
                            Instant hour, String key) throws java.sql.SQLException {
        try (var query = connection.prepareStatement("""
                SELECT count(*) FROM metric_label_value_admissions
                WHERE tenant_id=? AND metric_name=? AND hour_bucket=? AND label_key=?
                """)) {
            query.setString(1, tenant); query.setString(2, metric);
            query.setTimestamp(3, Timestamp.from(hour)); query.setString(4, key);
            try (var result = query.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
