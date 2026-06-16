package com.example.observability.server.phase3;

import com.example.observability.server.model.MetricPoint;
import com.example.observability.server.repository.TelemetryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Prosty detektor anomalii dla metryk.
 *
 * Ten serwis działa na gotowej serii punktów metrycznych,
 * pobranej wcześniej z TelemetryRepository przez controller albo job.
 *
 * Obsługiwane metody:
 * - rolling z-score,
 * - MAD, czyli Median Absolute Deviation.
 *
 * Cel:
 * - wykryć, czy najnowszy punkt metryki odstaje od wcześniejszego baseline'u,
 * - zapisać AnomalyEvent, jeśli ostatni punkt wygląda anomalnie,
 * - zwrócić wynik do API albo alertingu.
 *
 * To jest lekki detektor statystyczny dla MVP/Fazy 3,
 * nie pełny system ML/anomaly detection.
 */
@Service
public class AnomalyDetector {

    /**
     * Repozytorium używane tylko do zapisu wykrytych anomalii.
     *
     * Sam detektor nie pobiera danych z bazy.
     * Dostaje już przygotowaną listę MetricPoint jako argument.
     */
    private final TelemetryRepository repository;

    public AnomalyDetector(TelemetryRepository repository) {
        this.repository = repository;
    }

    /**
     * Wykrywa anomalię na podstawie rolling z-score.
     *
     * Założenie:
     * - bierzemy wszystkie punkty poza ostatnim jako baseline,
     * - liczymy średnią i odchylenie standardowe baseline'u,
     * - porównujemy ostatni punkt do baseline'u.
     *
     * Score:
     * z = (latest - mean) / std
     *
     * Interpretacja:
     * - |z| < 3.0  -> normal,
     * - |z| >= 3.0 -> warning,
     * - |z| >= 5.0 -> critical.
     *
     * Ta metoda dobrze działa dla metryk o w miarę stabilnym rozkładzie,
     * ale jest wrażliwa na outliery w baseline.
     */
    public AnomalyResult detectLatestZScore(
            String tenantId,
            String metricName,
            String service,
            List<MetricPoint> points
    ) {
        /*
         * Minimalna liczba punktów.
         *
         * Przy mniej niż 8 punktach baseline jest zbyt słaby,
         * więc nie próbujemy wykrywać anomalii.
         */
        if (points == null || points.size() < 8) {
            return new AnomalyResult(
                    tenantId,
                    metricName,
                    service,
                    false,
                    0.0,
                    0.0,
                    0.0,
                    "not-enough-data",
                    "info"
            );
        }

        /*
         * Sortujemy punkty po czasie, żeby ostatni punkt był faktycznie najnowszy.
         */
        List<MetricPoint> ordered = points
                .stream()
                .sorted(Comparator.comparing(MetricPoint::timestamp))
                .toList();

        /*
         * Baseline to wszystkie punkty poza najnowszym.
         *
         * Najnowszy punkt jest oceniany względem historii,
         * a nie używany do wyliczania średniej i odchylenia.
         */
        List<MetricPoint> baseline = ordered.subList(0, ordered.size() - 1);

        double mean = baseline
                .stream()
                .mapToDouble(MetricPoint::value)
                .average()
                .orElse(0.0);

        double variance = baseline
                .stream()
                .mapToDouble(p -> Math.pow(p.value() - mean, 2))
                .average()
                .orElse(0.0);

        double std = Math.sqrt(variance);

        double latest = ordered
                .get(ordered.size() - 1)
                .value();

        /*
         * Jeśli odchylenie standardowe wynosi 0,
         * wszystkie punkty baseline'u są takie same.
         *
         * W tej implementacji score ustawiamy na 0,
         * żeby uniknąć dzielenia przez zero.
         *
         * Uwaga:
         * produkcyjnie można tu potraktować latest != mean jako anomalię,
         * bo zmiana z płaskiej linii też może być istotna.
         */
        double z = std == 0.0
                ? 0.0
                : (latest - mean) / std;

        boolean anomalous = Math.abs(z) >= 3.0;

        String severity = Math.abs(z) >= 5.0
                ? "critical"
                : anomalous
                ? "warning"
                : "normal";

        AnomalyResult result = new AnomalyResult(
                tenantId,
                metricName,
                service,
                anomalous,
                z,
                mean,
                latest,
                "rolling-z-score",
                severity
        );

        /*
         * Zapisujemy event tylko wtedy, gdy anomalia faktycznie została wykryta.
         *
         * Dzięki temu tabela anomaly_events nie jest spamowana normalnymi wynikami.
         */
        if (anomalous) {
            repository.insertAnomalyEvent(result);
        }

        return result;
    }

    /**
     * Wykrywa anomalię metodą MAD: Median Absolute Deviation.
     *
     * MAD jest odporniejszy na outliery niż klasyczny z-score.
     *
     * Przepływ:
     * 1. Liczymy medianę wartości.
     * 2. Liczymy odchylenia bezwzględne od mediany.
     * 3. Liczymy medianę tych odchyleń, czyli MAD.
     * 4. Porównujemy ostatni punkt do mediany przez robust score.
     *
     * Score:
     * score = 0.6745 * (latest - median) / MAD
     *
     * Interpretacja:
     * - |score| < 3.5  -> normal,
     * - |score| >= 3.5 -> warning,
     * - |score| >= 6.0 -> critical.
     *
     * Ta metoda jest lepsza dla metryk, które mają pojedyncze skoki
     * albo baseline z outlierami.
     */
    public AnomalyResult detectLatestMad(
            String tenantId,
            String metricName,
            String service,
            List<MetricPoint> points
    ) {
        if (points == null || points.size() < 8) {
            return new AnomalyResult(
                    tenantId,
                    metricName,
                    service,
                    false,
                    0.0,
                    0.0,
                    0.0,
                    "not-enough-data",
                    "info"
            );
        }

        /*
         * Sortujemy same wartości, żeby policzyć medianę.
         *
         * Uwaga:
         * ta implementacja dla parzystej liczby punktów bierze element values.size()/2,
         * zamiast średniej z dwóch środkowych. Dla MVP wystarczy,
         * ale produkcyjnie warto policzyć medianę dokładniej.
         */
        List<Double> values = points
                .stream()
                .map(MetricPoint::value)
                .sorted()
                .toList();

        double median = values.get(values.size() / 2);

        /*
         * Odchylenia bezwzględne od mediany.
         *
         * MAD to mediana tych odchyleń.
         */
        List<Double> deviations = values
                .stream()
                .map(v -> Math.abs(v - median))
                .sorted()
                .toList();

        double mad = deviations.get(deviations.size() / 2);

        /*
         * Latest wybieramy po timestampie, nie po kolejności wejściowej.
         */
        double latest = points
                .stream()
                .max(Comparator.comparing(MetricPoint::timestamp))
                .orElse(points.get(points.size() - 1))
                .value();

        /*
         * 0.6745 skaluje MAD tak, żeby był porównywalny ze standard deviation
         * dla rozkładu normalnego.
         *
         * Jeśli MAD = 0, unikamy dzielenia przez zero.
         */
        double score = mad == 0.0
                ? 0.0
                : 0.6745 * (latest - median) / mad;

        boolean anomalous = Math.abs(score) >= 3.5;

        String severity = Math.abs(score) >= 6.0
                ? "critical"
                : anomalous
                ? "warning"
                : "normal";

        AnomalyResult result = new AnomalyResult(
                tenantId,
                metricName,
                service,
                anomalous,
                score,
                median,
                latest,
                "median-absolute-deviation",
                severity
        );

        if (anomalous) {
            repository.insertAnomalyEvent(result);
        }

        return result;
    }

    /**
     * Wynik detekcji anomalii.
     *
     * anomalous:
     * - true, jeśli najnowszy punkt został uznany za anomalię.
     *
     * score:
     * - z-score albo MAD score, zależnie od metody.
     *
     * baseline:
     * - średnia dla z-score,
     * - mediana dla MAD.
     *
     * observed:
     * - najnowsza zaobserwowana wartość metryki.
     *
     * method:
     * - rolling-z-score,
     * - median-absolute-deviation,
     * - not-enough-data.
     *
     * severity:
     * - info,
     * - normal,
     * - warning,
     * - critical.
     */
    public record AnomalyResult(
            String tenantId,
            String metricName,
            String service,
            boolean anomalous,
            double score,
            double baseline,
            double observed,
            String method,
            String severity
    ) {
        /**
         * Czas wygenerowania wyniku.
         *
         * To nie jest czas punktu metrycznego.
         * To czas wykonania detekcji.
         */
        public Instant detectedAt() {
            return Instant.now();
        }

        /**
         * Krótkie techniczne wyjaśnienie wyniku.
         *
         * Używane przy zapisie anomaly eventu
         * albo przy zwracaniu wyniku przez API.
         */
        public String explanation() {
            return method
                    + " score=" + score
                    + ", baseline=" + baseline
                    + ", observed=" + observed;
        }
    }
}