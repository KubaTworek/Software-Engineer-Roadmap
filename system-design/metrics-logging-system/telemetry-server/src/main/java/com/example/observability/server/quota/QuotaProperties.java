package com.example.observability.server.quota;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "telemetry.quotas")
public class QuotaProperties {
    private TenantQuota defaults = new TenantQuota();
    private Map<String, TenantQuota> tenants = new HashMap<>();

    public TenantQuota getDefaults() {
        return defaults;
    }

    public void setDefaults(TenantQuota defaults) {
        this.defaults = defaults;
    }

    public Map<String, TenantQuota> getTenants() {
        return tenants;
    }

    public void setTenants(Map<String, TenantQuota> tenants) {
        this.tenants = tenants;
    }

    public TenantQuota forTenant(String tenantId) {
        return tenants.getOrDefault(tenantId, defaults);
    }

    public static class TenantQuota {
        private int logsPerMinute = 100_000;
        private int metricSamplesPerMinute = 500_000;
        private int queryRequestsPerMinute = 600;
        private long maxQueryWindowSeconds = 30L * 24 * 3600;
        private int maxLogLimit = 5000;

        public int getLogsPerMinute() {
            return logsPerMinute;
        }

        public void setLogsPerMinute(int logsPerMinute) {
            this.logsPerMinute = logsPerMinute;
        }

        public int getMetricSamplesPerMinute() {
            return metricSamplesPerMinute;
        }

        public void setMetricSamplesPerMinute(int metricSamplesPerMinute) {
            this.metricSamplesPerMinute = metricSamplesPerMinute;
        }

        public int getQueryRequestsPerMinute() {
            return queryRequestsPerMinute;
        }

        public void setQueryRequestsPerMinute(int queryRequestsPerMinute) {
            this.queryRequestsPerMinute = queryRequestsPerMinute;
        }

        public long getMaxQueryWindowSeconds() {
            return maxQueryWindowSeconds;
        }

        public void setMaxQueryWindowSeconds(long maxQueryWindowSeconds) {
            this.maxQueryWindowSeconds = maxQueryWindowSeconds;
        }

        public int getMaxLogLimit() {
            return maxLogLimit;
        }

        public void setMaxLogLimit(int maxLogLimit) {
            this.maxLogLimit = maxLogLimit;
        }
    }
}
