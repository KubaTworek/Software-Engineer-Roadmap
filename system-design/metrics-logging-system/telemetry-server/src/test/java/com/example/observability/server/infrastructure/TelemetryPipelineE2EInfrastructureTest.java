package com.example.observability.server.infrastructure;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Najważniejszy test przekrojowy systemu. HTTP 202 samo w sobie nie dowodzi,
 * że telemetria jest dostępna: test czeka, aż rekord przejdzie przez Kafkę,
 * zostanie zapisany w ClickHouse i będzie możliwy do odczytania przez publiczne
 * query API. Oczekiwanie jawnie uwzględnia eventual consistency pipeline'u.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "telemetry.object-storage.export.enabled=false",
                "telemetry.alerting.evaluation-interval-ms=3600000",
                "telemetry.alerting.delivery-interval-ms=3600000",
                "telemetry.downsampling.cron=0 0 0 1 1 *"
        })
class TelemetryPipelineE2EInfrastructureTest {
    @Container
    static final ClickHouseContainer CLICKHOUSE = new ClickHouseContainer("clickhouse/clickhouse-server:24.8")
            .withDatabaseName("observability");
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void dependencies(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CLICKHOUSE::getJdbcUrl);
        registry.add("spring.datasource.username", CLICKHOUSE::getUsername);
        registry.add("spring.datasource.password", CLICKHOUSE::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("telemetry.metadata.url", POSTGRES::getJdbcUrl);
        registry.add("telemetry.metadata.username", POSTGRES::getUsername);
        registry.add("telemetry.metadata.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void initializeClickHouse() throws Exception {
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

    @Autowired
    TestRestTemplate http;

    @Test
    void acceptedLogEventuallyBecomesQueryable() throws Exception {
        String marker = "pipeline-e2e-" + System.nanoTime();
        HttpHeaders writer = headers("demo-writer-key");
        String body = """
                {"logs":[{"level":"ERROR","service":"checkout","host":"e2e",
                "message":"%s","attributes":{"scenario":"http-kafka-clickhouse"}}]}
                """.formatted(marker);

        var accepted = http.postForEntity("/api/v1/ingest/logs",
                new HttpEntity<>(body, writer), String.class);
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);

        HttpHeaders viewer = headers("demo-viewer-key");
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        String response = "";
        while (System.nanoTime() < deadline) {
            response = http.exchange("/api/v1/query/logs?tenantId=demo&service=checkout&limit=100",
                    HttpMethod.GET, new HttpEntity<>(viewer), String.class).getBody();
            if (response != null && response.contains(marker)) break;
            Thread.sleep(250);
        }
        assertThat(response).contains(marker).contains("http-kafka-clickhouse");
    }

    private HttpHeaders headers(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
