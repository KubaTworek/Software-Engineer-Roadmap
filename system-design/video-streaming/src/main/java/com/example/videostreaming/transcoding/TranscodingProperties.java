package com.example.videostreaming.transcoding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.transcoding")
public record TranscodingProperties(
        String ffmpegPath,
        boolean enabled,
        String workDir,
        int hlsSegmentSeconds,
        long pollIntervalMs
) {}
