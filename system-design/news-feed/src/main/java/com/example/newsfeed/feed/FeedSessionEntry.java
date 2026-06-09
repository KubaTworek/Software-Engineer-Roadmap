package com.example.newsfeed.feed;

import com.example.newsfeed.common.ConflictException;
import com.example.newsfeed.ranking.RankedFeedCandidate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public record FeedSessionEntry(
        UUID postId,
        FeedSource source,
        double score,
        String reason
) {
    public static FeedSessionEntry from(RankedFeedCandidate ranked) {
        return new FeedSessionEntry(
                ranked.candidate().post().getId(),
                ranked.candidate().source(),
                ranked.score(),
                ranked.reason()
        );
    }

    public String encode() {
        String raw = postId + "|" + source + "|" + score + "|" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(reason.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static FeedSessionEntry decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 4);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Invalid feed session entry.");
            }
            String reason = new String(Base64.getUrlDecoder().decode(parts[3]), StandardCharsets.UTF_8);
            return new FeedSessionEntry(
                    UUID.fromString(parts[0]),
                    FeedSource.valueOf(parts[1]),
                    Double.parseDouble(parts[2]),
                    reason
            );
        } catch (Exception exception) {
            throw new ConflictException("Invalid feed session entry.");
        }
    }
}
