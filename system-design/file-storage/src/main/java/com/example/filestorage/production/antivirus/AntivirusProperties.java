package com.example.filestorage.production.antivirus;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.production.antivirus")
public record AntivirusProperties(boolean enabled, String mode, String clamdHost, int clamdPort) {}
