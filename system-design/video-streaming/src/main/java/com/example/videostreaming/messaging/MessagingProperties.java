package com.example.videostreaming.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        String exchange,
        String transcodingQueue,
        String transcodingRoutingKey,
        String transcodingDlq,
        String transcodingDlqRoutingKey,
        String qoeQueue,
        String qoeRoutingKey,
        String qoeDlq,
        String qoeDlqRoutingKey,
        String liveStartQueue,
        String liveStartRoutingKey,
        String liveStopQueue,
        String liveStopRoutingKey,
        String liveDlq,
        String liveDlqRoutingKey,
        int maxAttempts,
        long initialRetryDelayMs
) {}
