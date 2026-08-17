package com.example.filestorage.production.backup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.production.backup")
public record BackupProperties(String localDir) {}
