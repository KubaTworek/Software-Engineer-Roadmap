package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single structured observability event.
 *
 * This class intentionally stores fields as a map because structured logs
 * often need dot-notated keys such as "service.name", "http.route", or "db.system.name".
 */
public final class StructuredLogEvent {

    private final Map<String, Object> fields;

    private StructuredLogEvent(Map<String, Object> fields) {
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, Object> fields() {
        return fields;
    }

    /**
     * Creates a copy that can be serialized by Jackson, Gson, or another JSON library.
     */
    public Map<String, Object> toMap() {
        return fields;
    }

    public static Builder builder(ServiceResource resource) {
        return new Builder(resource);
    }

    public static final class Builder {

        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder(ServiceResource resource) {
            if (resource == null) {
                throw new IllegalArgumentException("resource must not be null");
            }

            fields.put("timestamp", Instant.now().toString());
            fields.put("observed_timestamp", Instant.now().toString());
            fields.putAll(resource.toLogFields());
        }

        /**
         * Sets the event timestamp from the application perspective.
         */
        public Builder timestamp(Instant timestamp) {
            fields.put("timestamp", requireNonNull(timestamp, "timestamp").toString());
            return this;
        }

        /**
         * Sets the timestamp when the event was observed by the collecting system.
         */
        public Builder observedTimestamp(Instant observedTimestamp) {
            fields.put("observed_timestamp", requireNonNull(observedTimestamp, "observedTimestamp").toString());
            return this;
        }

        /**
         * Sets the normalized severity fields.
         */
        public Builder severity(LogSeverity severity) {
            LogSeverity value = requireNonNull(severity, "severity");
            fields.put("severity_text", value.severityText());
            fields.put("severity_number", value.severityNumber());
            return this;
        }

        /**
         * Sets the stable event name.
         */
        public Builder eventName(String eventName) {
            fields.put("event.name", requireNonBlank(eventName, "eventName"));
            return this;
        }

        /**
         * Sets a short human-readable event summary.
         */
        public Builder body(String body) {
            fields.put("body", requireNonBlank(body, "body"));
            return this;
        }

        /**
         * Adds request and trace correlation identifiers.
         */
        public Builder correlation(CorrelationContext context) {
            CorrelationContext value = requireNonNull(context, "context");

            fields.put("request_id", value.requestId());

            if (value.hasTraceContext()) {
                fields.put("trace_id", value.traceId());
            }

            if (value.hasSpanContext()) {
                fields.put("span_id", value.spanId());
            }

            return this;
        }

        /**
         * Adds one custom attribute.
         *
         * Do not use high-cardinality values such as user_id, email, request_id, trace_id,
         * raw URL, or full SQL query as metric labels. In logs they can also be risky,
         * so prefer stable and bounded attributes whenever possible.
         */
        public Builder attribute(String key, Object value) {
            fields.put(requireNonBlank(key, "key"), value);
            return this;
        }

        /**
         * Adds multiple custom attributes while preserving insertion order.
         */
        public Builder attributes(Map<String, ?> attributes) {
            if (attributes == null) {
                return this;
            }

            for (Map.Entry<String, ?> entry : attributes.entrySet()) {
                attribute(entry.getKey(), entry.getValue());
            }

            return this;
        }

        public StructuredLogEvent build() {
            requireField("severity_text");
            requireField("severity_number");
            requireField("event.name");
            requireField("body");
            requireField("service.name");
            requireField("deployment.environment.name");

            return new StructuredLogEvent(fields);
        }

        private void requireField(String fieldName) {
            if (!fields.containsKey(fieldName)) {
                throw new IllegalStateException("Missing required log field: " + fieldName);
            }
        }

        private static String requireNonBlank(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value;
        }

        private static <T> T requireNonNull(T value, String fieldName) {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " must not be null");
            }
            return value;
        }
    }
}