package pl.jakubtworek.booking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        long accessTokenSeconds,
        long refreshTokenSeconds
) {
    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            secret = "dev-only-stage-7-secret-key-change-me-minimum-32-characters";
        }
        if (accessTokenSeconds <= 0) {
            accessTokenSeconds = 900;
        }
        if (refreshTokenSeconds <= 0) {
            refreshTokenSeconds = 604800;
        }
    }
}
