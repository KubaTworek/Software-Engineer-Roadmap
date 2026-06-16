package com.example.observability.server.downsampling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job cyklicznie agregujący surowe metryki do tabel rollupowych.
 *
 * Cel:
 * - przyspieszyć zapytania po długich zakresach czasu,
 * - zmniejszyć liczbę punktów skanowanych przez query engine,
 * - umożliwić dłuższą retencję danych zagregowanych niż raw samples.
 *
 * Dane wejściowe:
 * - metrics_samples dla rollupu 1m,
 * - metrics_rollup_1m dla rollupu 5m,
 * - metrics_rollup_5m dla rollupu 1h.
 *
 * Dane wyjściowe:
 * - metrics_rollup_1m,
 * - metrics_rollup_5m,
 * - metrics_rollup_1h.
 *
 * To jest prosty downsampler MVP.
 * Produkcyjnie wymagałby idempotencji, markerów przetworzonych bucketów
 * i zabezpieczenia przed wielokrotnym insertowaniem tych samych rollupów.
 */
@Component
public class MetricsDownsampler {

    private static final Logger log = LoggerFactory.getLogger(MetricsDownsampler.class);

    /**
     * Dostęp do ClickHouse przez JDBC.
     *
     * Downsampler wykonuje INSERT INTO ... SELECT,
     * czyli agreguje dane bezpośrednio po stronie bazy.
     */
    private final JdbcTemplate jdbc;

    public MetricsDownsampler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Główna metoda uruchamiana cyklicznie przez Spring Scheduler.
     *
     * Harmonogram:
     * telemetry.downsampling.cron
     *
     * Domyślnie:
     *  0 *5 * * * *
     *
     * Czyli co 5 minut.
     *
     *  W jednym przebiegu buduje trzy poziomy rollupów:
     * - 1m z raw samples,
     * - 5m z rollupu 1m,
     * - 1h z rollupu 5m.
     */
    @Scheduled(cron = "${telemetry.downsampling.cron:0 */5 * * * *}")
    public void run() {
        try {
            /*
             * Rollup 1-minutowy.
             *
             * Źródło: metrics_samples.
             *
             * Zakres:
             * - od teraz - 2h,
             * - do teraz - 1m.
             *
             * Opóźnienie 1 minuty zmniejsza ryzyko agregowania danych,
             * które jeszcze spływają z ingestu.
             */
            downsample(
                    "metrics_rollup_1m",
                    "toStartOfMinute(timestamp)",
                    "now() - INTERVAL 2 HOUR",
                    "now() - INTERVAL 1 MINUTE"
            );

            /*
             * Rollup 5-minutowy.
             *
             * Źródło: metrics_rollup_1m.
             *
             * Zakres:
             * - od teraz - 7 dni,
             * - do teraz - 5 minut.
             *
             * Ten rollup jest przeznaczony do średnich zakresów czasu,
             * np. dashboardy z ostatnich dni.
             */
            downsample(
                    "metrics_rollup_5m",
                    "toStartOfInterval(timestamp, INTERVAL 5 MINUTE)",
                    "now() - INTERVAL 7 DAY",
                    "now() - INTERVAL 5 MINUTE"
            );

            /*
             * Rollup godzinowy.
             *
             * Źródło: metrics_rollup_5m.
             *
             * Zakres:
             * - od teraz - 90 dni,
             * - do teraz - 1 godzina.
             *
             * Ten rollup jest użyteczny dla długich query,
             * np. tygodnie/miesiące historii.
             */
            downsample(
                    "metrics_rollup_1h",
                    "toStartOfHour(timestamp)",
                    "now() - INTERVAL 90 DAY",
                    "now() - INTERVAL 1 HOUR"
            );

        } catch (Exception e) {
            /*
             * Błąd downsamplingu nie powinien zatrzymać aplikacji.
             *
             * Raw metrics nadal są dostępne.
             * Skutek awarii: query po długich zakresach może być wolniejsze
             * albo planner może nie mieć aktualnych rollupów.
             */
            log.warn("Metrics downsampling failed", e);
        }
    }

    /**
     * Wykonuje pojedynczy downsampling do wskazanej tabeli rollupowej.
     *
     * Parametry:
     * - table: tabela docelowa rollupu,
     * - bucketExpression: sposób zaokrąglania czasu do bucketu,
     * - startExpression: początek zakresu w SQL ClickHouse,
     * - endExpression: koniec zakresu w SQL ClickHouse.
     *
     * Metoda sama wybiera tabelę źródłową:
     * - metrics_rollup_1m czyta z metrics_samples,
     * - metrics_rollup_5m czyta z metrics_rollup_1m,
     * - metrics_rollup_1h czyta z metrics_rollup_5m.
     */
    private void downsample(
            String table,
            String bucketExpression,
            String startExpression,
            String endExpression
    ) {
        /*
         * Wybór źródła danych.
         *
         * Agregujemy kaskadowo:
         * raw -> 1m -> 5m -> 1h.
         *
         * Dzięki temu rollup godzinowy nie musi skanować raw samples
         * z ostatnich 90 dni.
         */
        String source = switch (table) {
            case "metrics_rollup_5m" -> "metrics_rollup_1m";
            case "metrics_rollup_1h" -> "metrics_rollup_5m";
            default -> "metrics_samples";
        };

        /*
         * Źródła raw i rollupowe mają inne nazwy kolumn czasu i wartości.
         *
         * metrics_samples:
         * - timestamp,
         * - value.
         *
         * metrics_rollup_*:
         * - bucket_start,
         * - avg_value.
         */
        String timeColumn = source.equals("metrics_samples")
                ? "timestamp"
                : "bucket_start";

        String valueColumn = source.equals("metrics_samples")
                ? "value"
                : "avg_value";

        /*
         * labels_json zostaje zachowany jako część grupowania.
         *
         * To oznacza, że rollup jest liczony osobno dla każdej kombinacji labeli.
         */
        String labelsColumn = source.equals("metrics_samples")
                ? "labels_json"
                : "labels_json";

        /*
         * Jeśli źródłem jest już rollup, bucketExpression musi operować
         * na bucket_start zamiast timestamp.
         *
         * Przykład:
         * toStartOfInterval(timestamp, INTERVAL 5 MINUTE)
         * zmienia się na:
         * toStartOfInterval(bucket_start, INTERVAL 5 MINUTE)
         */
        String bucket = source.equals("metrics_samples")
                ? bucketExpression
                : bucketExpression.replace("timestamp", "bucket_start");

        /*
         * INSERT INTO ... SELECT wykonuje agregację bezpośrednio w ClickHouse.
         *
         * Dla każdego tenant_id + metric_name + labels_json + bucket_start liczymy:
         * - avg_value,
         * - min_value,
         * - max_value,
         * - sample_count.
         */
        String sql = """
                INSERT INTO %s
                SELECT tenant_id,
                       metric_name,
                       %s AS bucket_start,
                       labels_json,
                       avg(%s) AS avg_value,
                       min(%s) AS min_value,
                       max(%s) AS max_value,
                       count() AS sample_count
                FROM %s
                WHERE %s >= %s AND %s < %s
                GROUP BY tenant_id, metric_name, bucket_start, %s
                """.formatted(
                table,
                bucket,
                valueColumn,
                valueColumn,
                valueColumn,
                source,
                timeColumn,
                startExpression,
                timeColumn,
                endExpression,
                labelsColumn
        );

        jdbc.execute(sql);

        log.debug("Downsampled {}", table);
    }
}