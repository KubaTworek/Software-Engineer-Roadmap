package com.example.urlshortener.enterprise;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.enterprise")
public class EnterpriseProperties {
    private boolean enabled = true;
    private String apiKeyHeader = "X-Api-Key";
    private String apiKeyHashSalt = "change-me-enterprise-salt";
    private int bulkMaxSize = 100;
    private int defaultRateLimitPerMinute = 1000;
    private Duration idempotencyTtl = Duration.ofHours(24);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKeyHeader() { return apiKeyHeader; }
    public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
    public String getApiKeyHashSalt() { return apiKeyHashSalt; }
    public void setApiKeyHashSalt(String apiKeyHashSalt) { this.apiKeyHashSalt = apiKeyHashSalt; }
    public int getBulkMaxSize() { return bulkMaxSize; }
    public void setBulkMaxSize(int bulkMaxSize) { this.bulkMaxSize = bulkMaxSize; }
    public int getDefaultRateLimitPerMinute() { return defaultRateLimitPerMinute; }
    public void setDefaultRateLimitPerMinute(int defaultRateLimitPerMinute) { this.defaultRateLimitPerMinute = defaultRateLimitPerMinute; }
    public Duration getIdempotencyTtl() { return idempotencyTtl; }
    public void setIdempotencyTtl(Duration idempotencyTtl) { this.idempotencyTtl = idempotencyTtl; }
}
