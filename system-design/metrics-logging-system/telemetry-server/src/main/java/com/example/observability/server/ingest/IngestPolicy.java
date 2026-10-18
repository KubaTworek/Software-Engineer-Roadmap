package com.example.observability.server.ingest;

import com.example.observability.server.model.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chroni pipeline przed payloadem poprawnym jako JSON, ale niebezpiecznym dla
 * storage: ogromnym batchem, timestampem spoza retencji lub nieznaną semantyką.
 */
@Service
public class IngestPolicy {
    private static final int MAX_BATCH = 10_000;
    private static final int MAX_MESSAGE = 256 * 1024;
    private static final Pattern METRIC_NAME = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");
    private static final Set<String> TYPES = Set.of("counter", "gauge", "histogram");
    private static final Set<String> TEMPORALITIES = Set.of("cumulative", "delta");
    private final Clock clock;

    public IngestPolicy(Clock clock) { this.clock = clock; }

    public void validate(LogIngestRequest request) {
        if (request.getLogs() == null || request.getLogs().isEmpty()) reject("logs batch is empty");
        if (request.getLogs().size() > MAX_BATCH) reject("logs batch exceeds " + MAX_BATCH);
        for (var log : request.getLogs()) {
            timestamp(log.getTimestamp());
            if (log.getMessage() != null && log.getMessage().length() > MAX_MESSAGE)
                reject("log message exceeds " + MAX_MESSAGE + " characters");
        }
    }

    public void validate(MetricIngestRequest request) {
        if (request.getSeries() == null || request.getSeries().isEmpty()) reject("metric batch is empty");
        int samples = 0;
        for (var series : request.getSeries()) {
            if (series.getName() == null || !METRIC_NAME.matcher(series.getName()).matches())
                reject("invalid metric name");
            if (series.getType() == null || series.getTemporality() == null)
                reject("metric type and temporality are required");
            String type = series.getType().toLowerCase(Locale.ROOT);
            String temporality = series.getTemporality().toLowerCase(Locale.ROOT);
            if (!TYPES.contains(type)) reject("unsupported metric type: " + type);
            if (!TEMPORALITIES.contains(temporality)) reject("unsupported temporality: " + temporality);
            boolean histogram = "histogram".equals(type);
            var scalarSamples = series.getSamples() == null ? List.<MetricSampleDto>of() : series.getSamples();
            var histogramPoints = series.getHistograms() == null ? List.<HistogramPointDto>of() : series.getHistograms();
            if (!histogram && scalarSamples.isEmpty())
                reject("metric series has no samples");
            for (var sample : scalarSamples) {
                timestamp(sample.getTimestamp());
                if (!Double.isFinite(sample.getValue())) reject("metric value must be finite");
                samples++;
            }
            if (histogram) {
                if (histogramPoints.isEmpty())
                    reject("histogram series has no histogram points");
                for (var point : histogramPoints) {
                    timestamp(point.getTimestamp());
                    if (point.getCount() < 0 || !Double.isFinite(point.getSum())) reject("invalid histogram count or sum");
                    if (point.getBucketCounts() == null || point.getExplicitBounds() == null
                            || point.getBucketCounts().size() != point.getExplicitBounds().size() + 1)
                        reject("histogram requires one bucket count per bound plus +Inf");
                    long previous = -1;
                    for (long count : point.getBucketCounts()) {
                        if (count < previous) reject("histogram bucket counts must be cumulative");
                        previous = count;
                    }
                    if (previous != point.getCount()) reject("+Inf bucket must equal histogram count");
                    for (int i = 1; i < point.getExplicitBounds().size(); i++)
                        if (point.getExplicitBounds().get(i) <= point.getExplicitBounds().get(i - 1))
                            reject("histogram bounds must be strictly increasing");
                    samples++;
                }
            }
        }
        if (samples > MAX_BATCH) reject("metric batch exceeds " + MAX_BATCH + " samples");
    }

    public void validate(TraceIngestRequest request) {
        if (request.getSpans() == null || request.getSpans().isEmpty()) reject("trace batch is empty");
        if (request.getSpans().size() > MAX_BATCH) reject("trace batch exceeds " + MAX_BATCH);
        for (var span : request.getSpans()) {
            timestamp(span.getStartTime());
            timestamp(span.getEndTime());
            if (span.getEndTime().isBefore(span.getStartTime())) reject("span ends before it starts");
        }
    }

    private void timestamp(Instant value) {
        if (value == null) reject("timestamp is required after normalization");
        Instant now = clock.instant();
        if (value.isAfter(now.plus(Duration.ofMinutes(5)))) reject("timestamp is too far in the future");
        if (value.isBefore(now.minus(Duration.ofDays(31)))) reject("timestamp is older than raw retention window");
    }

    private void reject(String reason) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
    }
}
