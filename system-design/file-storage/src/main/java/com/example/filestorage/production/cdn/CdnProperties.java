package com.example.filestorage.production.cdn;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.production.cdn")
public record CdnProperties(boolean enabled, String baseUrl, long signedUrlTtlSeconds) {}
