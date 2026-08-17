package com.example.observability.server.cold;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Job eksportujący stare dane telemetryczne z hot storage do cold storage.
 *
 * W tej aplikacji hot storage to ClickHouse,
 * a cold storage to ObjectStorageService, czyli abstrakcja nad storage'em obiektowym.
 *
 * Cel:
 * - zmniejszyć koszt długoterminowej retencji,
 * - przenieść starsze logi i metryki do tańszej warstwy,
 * - zachować możliwość późniejszej analizy offline.
 *
 * Eksportowany format:
 * - NDJSON, czyli jeden JSON na linię,
 * - kompresja gzip,
 * - osobne pliki dla logów i metryk,
 * - ścieżka partycjonowana po czasie yyyy/MM/dd/HH.
 *
 * Ważne:
 * ta klasa eksportuje dane, ale ich nie usuwa z ClickHouse.
 * Usuwanie/TTL powinno być obsługiwane osobno przez retention policy.
 */
@Component
public class ColdExportJob {

    private static final Logger log = LoggerFactory.getLogger(ColdExportJob.class);

    /**
     * Dostęp do hot storage.
     *
     * Stąd job czyta dane, które mają zostać wyeksportowane:
     * - tabela logs,
     * - tabela metrics_samples.
     */
    private final JdbcTemplate jdbc;

    /**
     * Mapper używany do serializacji każdego rekordu do JSON-a.
     *
     * Każdy rekord z ClickHouse staje się jedną linią NDJSON.
     */
    private final ObjectMapper objectMapper;

    /**
     * Abstrakcja nad cold/object storage.
     *
     * W lokalnym MVP może zapisywać na filesystem,
     * a produkcyjnie może zostać podmieniona na S3/GCS/Azure Blob/MinIO.
     */
    private final ObjectStorageService objectStorage;

    /**
     * Flaga włączająca/wyłączająca automatyczny eksport.
     *
     * Konfiguracja:
     * telemetry.object-storage.export.enabled
     */
    private final boolean enabled;

    /**
     * Określa, jak stare dane kwalifikują się do eksportu.
     *
     * Przykład:
     * exportAfterDays = 7 oznacza, że job eksportuje dane starsze niż 7 dni.
     *
     * Konfiguracja:
     * telemetry.object-storage.export.after-days
     */
    private final int exportAfterDays;

    public ColdExportJob(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ObjectStorageService objectStorage,
            @Value("${telemetry.object-storage.export.enabled:true}") boolean enabled,
            @Value("${telemetry.object-storage.export.after-days:7}") int exportAfterDays
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.objectStorage = objectStorage;
        this.enabled = enabled;
        this.exportAfterDays = exportAfterDays;
    }

    /**
     * Automatyczny eksport cold data.
     *
     * Harmonogram:
     * telemetry.object-storage.export.cron
     *
     * Domyślnie:
     * 0 15 * * * *
     *
     * Czyli raz na godzinę, w 15. minucie.
     *
     * Job eksportuje dokładnie jedną godzinę danych:
     * - od hourStart,
     * - do hourEnd.
     *
     * Zakres jest wyliczany jako godzina kończąca się exportAfterDays temu.
     *
     * Przykład:
     * jeśli teraz jest 2026-06-16 12:15 UTC,
     * a exportAfterDays = 7,
     * to eksportowana będzie godzina:
     * 2026-06-09 11:00 - 2026-06-09 12:00.
     */
    @Scheduled(cron = "${telemetry.object-storage.export.cron:0 15 * * * *}")
    public void exportHourlyColdData() {
        if (!enabled) {
            return;
        }

        /*
         * Wyznaczamy koniec eksportowanej godziny.
         *
         * Dane eksportujemy z opóźnieniem, żeby uniknąć eksportowania świeżych,
         * jeszcze spływających danych.
         */
        Instant hourEnd = Instant.now()
                .minusSeconds(exportAfterDays * 24L * 3600)
                .atZone(ZoneOffset.UTC)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();

        Instant hourStart = hourEnd.minusSeconds(3600);

        exportLogs(hourStart, hourEnd);
        exportMetrics(hourStart, hourEnd);
    }

    /**
     * Eksportuje logi z podanego zakresu czasu do cold storage.
     *
     * Zakres:
     * - timestamp >= start,
     * - timestamp < end.
     *
     * Dane są pobierane z tabeli logs i zapisywane jako:
     * logs/yyyy/MM/dd/HH/logs.ndjson.gz
     *
     * Każdy rekord jest serializowany do jednej linii JSON.
     */
    public String exportLogs(Instant start, Instant end) {
        List<String> lines = jdbc.query("""
                SELECT tenant_id, timestamp, level, service, host, trace_id, message, attributes_json
                FROM logs
                WHERE timestamp >= ? AND timestamp < ?
                LIMIT 100000
                """,
                (rs, i) -> toJson(Map.of(
                        "tenant_id", rs.getString("tenant_id"),
                        "timestamp", rs.getTimestamp("timestamp").toInstant().toString(),
                        "level", rs.getString("level"),
                        "service", rs.getString("service"),
                        "host", rs.getString("host"),
                        "trace_id", rs.getString("trace_id"),
                        "message", rs.getString("message"),
                        "attributes_json", rs.getString("attributes_json")
                )),
                Timestamp.from(start),
                Timestamp.from(end)
        );

        /*
         * Klucz obiektu jest partycjonowany po czasie.
         *
         * To ułatwia:
         * - lifecycle policies,
         * - późniejszy import,
         * - ręczne wyszukiwanie danych,
         * - kompatybilność z narzędziami batchowymi.
         */
        String key = "logs/"
                + DateTimeFormatter.ofPattern("yyyy/MM/dd/HH")
                .withZone(ZoneOffset.UTC)
                .format(start)
                + "/logs.ndjson.gz";

        String uri = objectStorage.putGzipLines(key, lines);

        log.info("Exported {} log rows to {}", lines.size(), uri);

        return uri;
    }

    /**
     * Eksportuje próbki metryk raw z podanego zakresu czasu do cold storage.
     *
     * Zakres:
     * - timestamp >= start,
     * - timestamp < end.
     *
     * Dane są pobierane z tabeli metrics_samples i zapisywane jako:
     * metrics/yyyy/MM/dd/HH/metrics.ndjson.gz
     *
     * Eksportowane są raw samples, nie rollupy.
     */
    public String exportMetrics(Instant start, Instant end) {
        List<String> lines = jdbc.query("""
                SELECT tenant_id, metric_name, timestamp, value, labels_json
                FROM metrics_samples
                WHERE timestamp >= ? AND timestamp < ?
                LIMIT 200000
                """,
                (rs, i) -> toJson(Map.of(
                        "tenant_id", rs.getString("tenant_id"),
                        "metric_name", rs.getString("metric_name"),
                        "timestamp", rs.getTimestamp("timestamp").toInstant().toString(),
                        "value", rs.getDouble("value"),
                        "labels_json", rs.getString("labels_json")
                )),
                Timestamp.from(start),
                Timestamp.from(end)
        );

        String key = "metrics/"
                + DateTimeFormatter.ofPattern("yyyy/MM/dd/HH")
                .withZone(ZoneOffset.UTC)
                .format(start)
                + "/metrics.ndjson.gz";

        String uri = objectStorage.putGzipLines(key, lines);

        log.info("Exported {} metric rows to {}", lines.size(), uri);

        return uri;
    }

    /**
     * Bezpieczna serializacja rekordu do JSON-a.
     *
     * Jeśli pojedynczy rekord nie da się zserializować,
     * metoda zwraca pusty obiekt JSON.
     *
     * Dzięki temu eksport nie wywraca się na jednym błędnym rekordzie.
     *
     * Uwaga:
     * w produkcji warto logować taki błąd i zliczać go metryką,
     * bo "{}" w cold storage oznacza utratę konkretnego rekordu.
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}