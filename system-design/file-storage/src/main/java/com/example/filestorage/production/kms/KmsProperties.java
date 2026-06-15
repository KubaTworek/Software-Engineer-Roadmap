package com.example.filestorage.production.kms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.production.kms")
public record KmsProperties(String masterKey) {}
