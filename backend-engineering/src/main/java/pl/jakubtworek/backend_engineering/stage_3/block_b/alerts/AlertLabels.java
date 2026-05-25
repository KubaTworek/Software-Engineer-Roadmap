package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents alert labels used for routing, grouping, silencing, and alert identity.
 *
 * Labels must be stable and low-cardinality. Do not put request_id, trace_id,
 * user_id, raw URL, exception message, or full SQL into alert labels.
 */
public final class AlertLabels {

    private final Map<String, String> values;

    private AlertLabels(Builder builder) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(builder.values));
    }

    public Map<String, String> values() {
        return values;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, String> values = new LinkedHashMap<>();

        public Builder severity(AlertSeverity severity) {
            return put("severity", severity.labelValue());
        }

        public Builder priority(AlertPriority priority) {
            return put("priority", priority.labelValue());
        }

        public Builder team(String team) {
            return put("team", team);
        }

        public Builder service(String service) {
            return put("service", service);
        }

        public Builder cluster(String cluster) {
            return put("cluster", cluster);
        }

        public Builder environment(String environment) {
            return put("environment", environment);
        }

        public Builder put(String key, String value) {
            validateLabel(key, value);
            values.put(key, value);
            return this;
        }

        public AlertLabels build() {
            require("severity");
            require("team");
            require("service");

            return new AlertLabels(this);
        }

        private void require(String key) {
            if (!values.containsKey(key)) {
                throw new IllegalStateException("Missing required alert label: " + key);
            }
        }

        private static void validateLabel(String key, String value) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("alert label key must not be blank");
            }

            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("alert label value must not be blank: " + key);
            }

            if (value.length() > 120) {
                throw new IllegalArgumentException("alert label value is suspiciously long: " + key);
            }

            String normalized = key.toLowerCase();

            if (normalized.contains("trace")
                    || normalized.contains("request")
                    || normalized.contains("user")
                    || normalized.contains("email")
                    || normalized.contains("sql")
                    || normalized.contains("exception")) {
                throw new IllegalArgumentException("high-cardinality or sensitive label is not allowed: " + key);
            }
        }
    }
}