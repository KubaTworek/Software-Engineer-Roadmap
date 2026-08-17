package com.example.filestorage.production.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.production.rate-limit")
public record RateLimitProperties(boolean enabled, int requestsPerMinute) {}
