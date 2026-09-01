package pl.jakubtworek.backend_engineering.stage_1.block_c.configuration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/** Typowana konfiguracja klienta zewnętrznego, walidowana podczas startu. */
@Validated
@ConfigurationProperties(prefix = "app.external-api")
public record ValidatedExternalApiProperties(
        @NotNull URI baseUrl,
        String apiKey,
        @NotNull Duration timeout
) {
    public ValidatedExternalApiProperties {
        if (baseUrl != null && !("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))) {
            throw new IllegalArgumentException("baseUrl must use http or https");
        }
        if (timeout != null
                && (timeout.compareTo(Duration.ofMillis(100)) < 0
                || timeout.compareTo(Duration.ofSeconds(30)) > 0)) {
            throw new IllegalArgumentException("timeout must be between 100ms and 30s");
        }
    }
}
