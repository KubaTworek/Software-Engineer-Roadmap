package com.example.filestorage.production.thumbnail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.production.thumbnails")
public record ThumbnailProperties(boolean enabled, int maxWidth, int maxHeight) {}
