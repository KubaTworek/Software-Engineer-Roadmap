package com.example.filestorage.production.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.production.outbox")
public record OutboxProperties(long publishFixedDelayMs, int maxAttempts) {}
