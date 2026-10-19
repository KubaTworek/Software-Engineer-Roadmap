package com.example.ratelimiter.security;

import org.junit.jupiter.api.Test;
import com.example.ratelimiter.config.RateLimiterProperties;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    @Test
    void shouldHashApiKeyUsingSha256() {
        ApiKeyHasher hasher = hasher();

        String first = hasher.hmacSha256("secret-api-key");
        String second = hasher.hmacSha256("secret-api-key");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
        assertThat(first).isNotEqualTo("secret-api-key");
    }

    @Test
    void shouldReturnNullForBlankApiKey() {
        ApiKeyHasher hasher = hasher();

        assertThat(hasher.hmacSha256(null)).isNull();
        assertThat(hasher.hmacSha256("   ")).isNull();
    }

    private ApiKeyHasher hasher() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getSecurity().setIdentityHmacSecret("test-secret");
        return new ApiKeyHasher(properties);
    }
}
