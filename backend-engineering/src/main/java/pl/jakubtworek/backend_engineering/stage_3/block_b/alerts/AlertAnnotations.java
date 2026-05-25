package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents alert annotations.
 *
 * Annotations should explain the alert to a human responder.
 * They are the right place for summary, description, dashboard URL, and runbook URL.
 */
public final class AlertAnnotations {

    private final Map<String, String> values;

    private AlertAnnotations(Builder builder) {
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

        public Builder summary(String summary) {
            return put("summary", summary);
        }

        public Builder description(String description) {
            return put("description", description);
        }

        public Builder runbookUrl(String runbookUrl) {
            return put("runbook_url", runbookUrl);
        }

        public Builder dashboardUrl(String dashboardUrl) {
            return put("dashboard_url", dashboardUrl);
        }

        public Builder put(String key, String value) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("annotation key must not be blank");
            }

            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("annotation value must not be blank: " + key);
            }

            values.put(key, value);
            return this;
        }

        public AlertAnnotations build() {
            require("summary");
            require("description");
            require("runbook_url");

            return new AlertAnnotations(this);
        }

        private void require(String key) {
            if (!values.containsKey(key)) {
                throw new IllegalStateException("Missing required alert annotation: " + key);
            }
        }
    }
}