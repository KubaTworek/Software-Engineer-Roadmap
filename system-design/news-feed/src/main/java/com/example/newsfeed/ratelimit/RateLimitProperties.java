package com.example.newsfeed.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "newsfeed.rate-limit")
public record RateLimitProperties(
        int feedPerMinute,
        int postCreatePerMinute,
        int writePerMinute
) {
}
