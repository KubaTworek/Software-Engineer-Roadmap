package com.example.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource")
public record ReadReplicaProperties(
    boolean readReplicaEnabled,
    String writeUrl,
    String writeUsername,
    String writePassword,
    String readUrl,
    String readUsername,
    String readPassword
) {
}
