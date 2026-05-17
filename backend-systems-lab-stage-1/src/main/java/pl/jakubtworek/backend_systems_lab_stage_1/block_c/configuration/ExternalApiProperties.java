package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration class.
 *
 * Recommended over multiple @Value annotations.
 *
 * Properties with prefix:
 * app.external-api
 *
 * will be mapped automatically.
 */
@ConfigurationProperties(prefix = "app.external-api")
public class ExternalApiProperties {

    /**
     * Example:
     * app.external-api.base-url=https://api.example.com
     */
    private String baseUrl;

    /**
     * Example:
     * app.external-api.api-key=secret
     */
    private String apiKey;

    /**
     * Example:
     * app.external-api.timeout=5000
     */
    private Integer timeout;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
}