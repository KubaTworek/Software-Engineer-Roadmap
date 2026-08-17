package com.example.newsfeed.feed;

import com.example.newsfeed.common.ConflictException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public record FeedSessionCursor(
        UUID sessionId,
        int offset
) {
    public String encode() {
        String raw = sessionId + "|" + offset;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<FeedSessionCursor> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }

        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid feed cursor format.");
            }
            return Optional.of(new FeedSessionCursor(UUID.fromString(parts[0]), Integer.parseInt(parts[1])));
        } catch (Exception exception) {
            throw new ConflictException("Invalid feed cursor.");
        }
    }
}
