package com.example.observability.server.infrastructure;

import org.junit.jupiter.api.Test;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import com.example.observability.server.repository.TelemetryRepository;
import com.example.observability.server.model.LogIngestRequest;
import com.example.observability.server.model.LogEventDto;
import com.example.observability.server.model.MetricIngestRequest;
import com.example.observability.server.model.MetricPoint;
import com.example.observability.server.model.MetricSampleDto;
import com.example.observability.server.model.MetricSeriesDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.Instant;
import java.util.List;

/**
 * Kontrakt prawdziwego magazynu telemetrycznego. Dane różnych tenantów mogą
 * współdzielić tabelę, ale każde zapytanie musi zawężać odczyt kluczem tenant_id.
 */
@Testcontainers(disabledWithoutDocker = true)
class ClickHouseTenantIsolationInfrastructureTest {

    @Container
    static final ClickHouseContainer CLICKHOUSE = new ClickHouseContainer("clickhouse/clickhouse-server:24.8");

    @Test
    void repositorySchemaIsExecutableOnSupportedClickHouse() throws Exception {
        String schema = Files.readString(Path.of("../clickhouse/init/01_schema.sql"));
        try (var connection = CLICKHOUSE.createConnection("");
             var statement = connection.createStatement()) {
            for (String sql : schema.split(";")) {
                String withoutComments = sql.lines()
                        .filter(line -> !line.stripLeading().startsWith("--"))
                        .collect(java.util.stream.Collectors.joining("\n")).trim();
                if (!withoutComments.isBlank()) statement.execute(withoutComments);
            }
        }
    }

    @Test
    void tenantPredicateDoesNotReturnAnotherTenantsSamples() throws Exception {
        try (var connection = CLICKHOUSE.createConnection("")) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE metric_samples(
                            tenant_id LowCardinality(String),
                            metric_name LowCardinality(String),
                            value UInt64,
                            observed_at DateTime64(3)
                        ) ENGINE = MergeTree
                        ORDER BY (tenant_id, metric_name, observed_at)
                        """);
                statement.execute("""
                        INSERT INTO metric_samples VALUES
                        ('tenant-a', 'requests', 7, now64(3)),
                        ('tenant-b', 'requests', 900, now64(3))
                        """);
            }

            try (var query = connection.prepareStatement(
                    "SELECT sum(value) FROM metric_samples WHERE tenant_id = ?")) {
                query.setString(1, "tenant-a");
                try (var result = query.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isEqualTo(7);
                }
            }
        }
    }

    @Test
    void replayIsDeduplicatedAndRollupUsesWeightedAverage() throws Exception {
        try (var connection = CLICKHOUSE.createConnection("")) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE raw_rollup_lab(
                          event_id UUID, bucket UInt8, value Float64, ingested_at DateTime64(3)
                        ) ENGINE=ReplacingMergeTree(ingested_at) ORDER BY (bucket,event_id)
                        """);
                statement.execute("""
                        INSERT INTO raw_rollup_lab VALUES
                        ('00000000-0000-0000-0000-000000000001', 1, 10, now64(3)),
                        ('00000000-0000-0000-0000-000000000002', 1, 20, now64(3)),
                        ('00000000-0000-0000-0000-000000000003', 2, 100, now64(3)),
                        ('00000000-0000-0000-0000-000000000001', 1, 10, now64(3))
                        """);
            }
            try (var result = connection.createStatement().executeQuery(
                    "SELECT count(), avg(value), min(value), max(value) FROM raw_rollup_lab FINAL")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(3);
                assertThat(result.getDouble(2)).isEqualTo(130.0 / 3.0);
                assertThat(result.getDouble(3)).isEqualTo(10.0);
                assertThat(result.getDouble(4)).isEqualTo(100.0);
            }
        }
    }

    @Test
    void bloomSegmentsForSameHourAreNeverReplaced() throws Exception {
        try (var connection = CLICKHOUSE.createConnection("")) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE bloom_segment_lab(
                          tenant String, hour DateTime, segment_id UUID, bits String
                        ) ENGINE=MergeTree ORDER BY (tenant,hour,segment_id)
                        """);
                statement.execute("""
                        INSERT INTO bloom_segment_lab VALUES
                        ('demo', toDateTime('2026-01-01 00:00:00'), generateUUIDv4(), 'first'),
                        ('demo', toDateTime('2026-01-01 00:00:00'), generateUUIDv4(), 'second')
                        """);
            }
            try (var result = connection.createStatement().executeQuery(
                    "SELECT count() FROM bloom_segment_lab WHERE tenant='demo'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void replayOfTheSameLogDoesNotDoubleCountFullTextDocument() throws Exception {
        ensureRepositorySchema();
        String eventId = "00000000-0000-0000-0000-000000000042";
        try (var connection = CLICKHOUSE.createConnection("");
             var statement = connection.createStatement()) {
            // now64() is constant within this INSERT: both deliveries retain the
            // same sorting key, but stay inside the real 30-day retention window.
            // A historical fixture races ClickHouse's asynchronous TTL cleanup.
            statement.execute("""
                    INSERT INTO observability.log_fulltext_terms
                    (event_id, tenant_id, service, level, bucket_start, term, doc_count, sample_trace_ids)
                    VALUES
                    ('%s','tenant-replay','api','ERROR',now64(3),'timeout',1,[]),
                    ('%s','tenant-replay','api','ERROR',now64(3),'timeout',1,[])
                    """.formatted(eventId, eventId));
            // Check retention during a forced merge too, rather than depending on
            // whether the background merge happens before or after the assertion.
            statement.execute("OPTIMIZE TABLE observability.log_fulltext_terms FINAL");
            try (var result = statement.executeQuery("""
                    SELECT count(), sum(doc_count) FROM observability.log_fulltext_terms FINAL
                    WHERE tenant_id='tenant-replay' AND term='timeout'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(1);
                assertThat(result.getLong(2)).isEqualTo(1);
            }
        }
    }

    @Test
    void histogramPersistsCumulativeBucketsAndExemplar() throws Exception {
        ensureRepositorySchema();
        try (var connection = CLICKHOUSE.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO observability.metric_histograms
                    (event_id,tenant_id,metric_name,timestamp,count,sum,explicit_bounds,bucket_counts,labels_json,exemplar_trace_id)
                    VALUES ('00000000-0000-0000-0000-000000000043','tenant-hist','http_duration_seconds',now64(3),
                            5,1.7,[0.1,0.5],[2,4,5],'{\"service\":\"api\"}','trace-42')
                    """);
            try (var result = statement.executeQuery("""
                    SELECT count, sum, arrayElement(bucket_counts, 3), exemplar_trace_id
                    FROM observability.metric_histograms FINAL
                    WHERE tenant_id='tenant-hist'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(5);
                assertThat(result.getDouble(2)).isEqualTo(1.7);
                assertThat(result.getLong(3)).isEqualTo(5);
                assertThat(result.getString(4)).isEqualTo("trace-42");
            }
        }
    }

    private void ensureRepositorySchema() throws Exception {
        String schema = Files.readString(Path.of("../clickhouse/init/01_schema.sql"));
        try (var connection = CLICKHOUSE.createConnection("");
             var statement = connection.createStatement()) {
            for (String sql : schema.split(";")) {
                String executable = sql.lines()
                        .filter(line -> !line.stripLeading().startsWith("--"))
                        .collect(java.util.stream.Collectors.joining("\n")).trim();
                if (!executable.isBlank()) statement.execute(executable);
            }
        }
    }

    @Test
    void repositoryJdbcContractWritesUuidAndReadsTheLogicalEvent() throws Exception {
        ensureRepositorySchema();
        String url = CLICKHOUSE.getJdbcUrl().replace("/default", "/observability");
        var dataSource = new DriverManagerDataSource(url, CLICKHOUSE.getUsername(), CLICKHOUSE.getPassword());
        dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        var repository = new TelemetryRepository(new JdbcTemplate(dataSource), new ObjectMapper(), 100, 5000);
        var event = new LogEventDto();
        event.setEventId("00000000-0000-0000-0000-000000000099");
        event.setTimestamp(Instant.now());
        event.setLevel("ERROR"); event.setService("jdbc-contract"); event.setHost("test");
        event.setMessage("repository contract");
        var request = new LogIngestRequest();
        request.setTenantId("tenant-jdbc"); request.setLogs(List.of(event));

        repository.insertLogs(request);

        assertThat(repository.queryLogs("tenant-jdbc", null, null, null,
                null, null, 10)).hasSize(1);
        assertThat(repository.queryLogs("tenant-jdbc", "jdbc-contract", null, null,
                null, null, 10)).hasSize(1);
        assertThat(repository.queryLogs("tenant-jdbc", null, null, null,
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 10)).hasSize(1);
    }

    @Test
    void repositoryMetricWindowKeepsUtcWhenJvmAndClickHouseZonesDiffer() throws Exception {
        ensureRepositorySchema();
        String url = CLICKHOUSE.getJdbcUrl().replace("/default", "/observability");
        var dataSource = new DriverManagerDataSource(url, CLICKHOUSE.getUsername(), CLICKHOUSE.getPassword());
        dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        var repository = new TelemetryRepository(new JdbcTemplate(dataSource), new ObjectMapper(), 100, 5000);

        // ClickHouse evaluates the real 30-day retention TTL. A fixed historical
        // fixture eventually expires, turning a timezone test into a retention test.
        Instant observedAt = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var sample = new MetricSampleDto();
        sample.setEventId("00000000-0000-0000-0000-000000000100");
        sample.setTimestamp(observedAt);
        sample.setValue(42.0);
        var series = new MetricSeriesDto();
        series.setName("request_count");
        series.setSamples(List.of(sample));
        var request = new MetricIngestRequest();
        request.setTenantId("tenant-metric-jdbc");
        request.setSeries(List.of(series));

        repository.insertMetrics(request);

        assertThat(repository.queryMetricSeries("metrics_samples", "tenant-metric-jdbc",
                "request_count", null, observedAt.minusSeconds(1), observedAt.plusSeconds(1), 60))
                .singleElement()
                .extracting(MetricPoint::value)
                .isEqualTo(42.0);
    }
}
