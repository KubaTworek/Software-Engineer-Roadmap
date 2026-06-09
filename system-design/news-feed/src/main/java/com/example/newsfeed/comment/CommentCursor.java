package com.example.newsfeed.comment;

import com.example.newsfeed.common.ConflictException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Cursor używany do paginacji komentarzy.
 *
 * Cursor zawiera dwie wartości:
 * - createdAt ostatniego komentarza z poprzedniej strony,
 * - id ostatniego komentarza z poprzedniej strony.
 *
 * Dzięki temu możemy pobrać kolejną stronę bez używania OFFSET.
 */
public record CommentCursor(
        Instant createdAt,
        UUID id
) {

    /**
     * Koduje cursor do formatu bezpiecznego dla URL.
     *
     * Surowy format:
     * createdAt|commentId
     *
     * Następnie całość jest kodowana przez Base64 URL-safe.
     */
    public String encode() {
        String raw = createdAt.toString() + "|" + id;

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Dekoduje cursor z requestu.
     *
     * Jeśli cursor nie został podany, zwracamy Optional.empty(),
     * czyli klient pobiera pierwszą stronę komentarzy.
     *
     * Jeśli cursor ma zły format, rzucamy ConflictException,
     * żeby nie wykonywać zapytania na błędnych danych wejściowych.
     */
    public static Optional<CommentCursor> decode(String cursor) {
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

            return Optional.of(new CommentCursor(
                    Instant.parse(parts[0]),
                    UUID.fromString(parts[1])
            ));
        } catch (Exception exception) {
            throw new ConflictException("Invalid comment cursor.");
        }
    }
}