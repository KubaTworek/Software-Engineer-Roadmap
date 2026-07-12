package com.example.videostreaming.cdn;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cdn")
public record CdnProperties(
        boolean prewarmEnabled,
        int prewarmMaxObjects,
        String provider,
        int connectTimeoutMs,
        long popularPrewarmIntervalMs,
        int popularPrewarmLimit
) {}
