package com.example.newsfeed.feed;

import com.example.newsfeed.common.ConflictException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public record FeedCursor(
        Instant createdAt,
        UUID id
) {
    public String encode() {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<FeedCursor> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);

            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor format.");
            }

            return Optional.of(new FeedCursor(
                    Instant.parse(parts[0]),
                    UUID.fromString(parts[1])
            ));
        } catch (Exception exception) {
            throw new ConflictException("Invalid feed cursor.");
        }
    }
}
