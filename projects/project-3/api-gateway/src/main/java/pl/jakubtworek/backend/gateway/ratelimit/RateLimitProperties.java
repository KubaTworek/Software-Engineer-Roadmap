package pl.jakubtworek.backend.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        Bucket anonymous,
        Bucket apiKey
) {
    public RateLimitProperties {
        if (anonymous == null) {
            anonymous = new Bucket(60, 60, Duration.ofMinutes(1));
        }
        if (apiKey == null) {
            apiKey = new Bucket(600, 600, Duration.ofMinutes(1));
        }
    }

    public record Bucket(long capacity, long refillTokens, Duration refillPeriod) {
    }
}
