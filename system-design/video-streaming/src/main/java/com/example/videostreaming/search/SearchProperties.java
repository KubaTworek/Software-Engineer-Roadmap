package com.example.videostreaming.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(String endpoint, String index, boolean enabled) {}
