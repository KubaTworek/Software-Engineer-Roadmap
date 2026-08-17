package com.example.filestorage.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String publicEndpoint
) {}
