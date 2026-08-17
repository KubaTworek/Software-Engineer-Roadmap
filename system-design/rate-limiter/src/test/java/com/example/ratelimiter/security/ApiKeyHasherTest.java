package com.example.ratelimiter.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    @Test
    void shouldHashApiKeyUsingSha256() {
        ApiKeyHasher hasher = new ApiKeyHasher();

        String first = hasher.sha256("secret-api-key");
        String second = hasher.sha256("secret-api-key");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
        assertThat(first).isNotEqualTo("secret-api-key");
    }

    @Test
    void shouldReturnNullForBlankApiKey() {
        ApiKeyHasher hasher = new ApiKeyHasher();

        assertThat(hasher.sha256(null)).isNull();
        assertThat(hasher.sha256("   ")).isNull();
    }
}
