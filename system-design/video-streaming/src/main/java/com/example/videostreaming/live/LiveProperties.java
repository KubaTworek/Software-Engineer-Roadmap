package com.example.videostreaming.live;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.live")
public record LiveProperties(
        boolean enabled,
        boolean workerEnabled,
        String ffmpegPath,
        String publicIngestBaseUrl,
        String internalIngestBaseUrl,
        String workDir,
        int standardSegmentSeconds,
        int lowLatencySegmentSeconds,
        int defaultDvrWindowSeconds,
        int maxDvrWindowSeconds,
        boolean liveToVodEnabled
) {}
