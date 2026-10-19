package com.example.urlshortener.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ShortUrlTest {
    @Test
    void expiresAtIsExclusiveBoundary() {
        Instant expiry = Instant.parse("2026-09-14T10:15:30Z");
        var url = new ShortUrl(1L, "abc", "https://example.com", expiry);

        assertThat(url.isExpired(expiry.minusNanos(1))).isFalse();
        assertThat(url.isExpired(expiry)).isTrue();
        assertThat(url.isExpired(expiry.plusNanos(1))).isTrue();
    }
}
