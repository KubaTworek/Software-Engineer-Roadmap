package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the logical source of telemetry.
 *
 * In OpenTelemetry terms, this maps to Resource attributes.
 * These values should be stable across logs, metrics, and traces.
 */
public final class ServiceResource {

    private final String serviceName;
    private final String serviceVersion;
    private final String deploymentEnvironmentName;
    private final String serviceInstanceId;

    private ServiceResource(Builder builder) {
        this.serviceName = requireNonBlank(builder.serviceName, "serviceName");
        this.serviceVersion = builder.serviceVersion;
        this.deploymentEnvironmentName =
                requireNonBlank(builder.deploymentEnvironmentName, "deploymentEnvironmentName");
        this.serviceInstanceId = builder.serviceInstanceId != null
                ? builder.serviceInstanceId
                : UUID.randomUUID().toString();
    }

    public String serviceName() {
        return serviceName;
    }

    public String serviceVersion() {
        return serviceVersion;
    }

    public String deploymentEnvironmentName() {
        return deploymentEnvironmentName;
    }

    public String serviceInstanceId() {
        return serviceInstanceId;
    }

    /**
     * Converts resource attributes to stable log fields.
     *
     * Dot notation is intentionally used to match common OpenTelemetry conventions.
     */
    public Map<String, Object> toLogFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("service.name", serviceName);
        fields.put("deployment.environment.name", deploymentEnvironmentName);
        fields.put("service.instance.id", serviceInstanceId);

        if (serviceVersion != null && !serviceVersion.isBlank()) {
            fields.put("service.version", serviceVersion);
        }

        return Collections.unmodifiableMap(fields);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public static final class Builder {
        private String serviceName;
        private String serviceVersion;
        private String deploymentEnvironmentName;
        private String serviceInstanceId;

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        public Builder deploymentEnvironmentName(String deploymentEnvironmentName) {
            this.deploymentEnvironmentName = deploymentEnvironmentName;
            return this;
        }

        public Builder serviceInstanceId(String serviceInstanceId) {
            this.serviceInstanceId = serviceInstanceId;
            return this;
        }

        public ServiceResource build() {
            return new ServiceResource(this);
        }
    }
}