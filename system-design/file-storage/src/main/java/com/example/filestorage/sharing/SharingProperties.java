package com.example.filestorage.sharing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sharing")
public record SharingProperties(String publicBaseUrl) {}
