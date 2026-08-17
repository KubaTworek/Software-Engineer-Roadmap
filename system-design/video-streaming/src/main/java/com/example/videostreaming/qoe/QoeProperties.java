package com.example.videostreaming.qoe;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.qoe")
public record QoeProperties(boolean enabled) {}
