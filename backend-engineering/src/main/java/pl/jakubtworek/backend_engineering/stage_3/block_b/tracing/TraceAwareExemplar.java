package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents exemplar metadata that can connect a metric point to a trace.
 *
 * The key idea is to attach trace_id and span_id as exemplar metadata,
 * not as normal Prometheus labels.
 */
public final class TraceAwareExemplar {

    private final Map<String, String> labels;

    private TraceAwareExemplar(Map<String, String> labels) {
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels));
    }

    public Map<String, String> labels() {
        return labels;
    }

    public static TraceAwareExemplar current() {
        TraceContextSnapshot snapshot = TraceContextSnapshot.current();

        if (!snapshot.valid()) {
            return new TraceAwareExemplar(Map.of());
        }

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("trace_id", snapshot.traceId());
        labels.put("span_id", snapshot.spanId());

        return new TraceAwareExemplar(labels);
    }
}