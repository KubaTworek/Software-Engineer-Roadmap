package com.example.newsfeed.search;

import java.time.Instant;
import java.util.UUID;

/**
 * Dokument zapisywany do indeksu OpenSearch.
 *
 * To nie jest encja JPA.
 * To jest płaski dokument wyszukiwarkowy zoptymalizowany pod search.
 *
 * OpenSearch powinien przechowywać dane potrzebne do znalezienia wyników,
 * a PostgreSQL dalej pozostaje źródłem prawdy dla pełnego posta.
 */
public record PostSearchDocument(
        UUID postId,
        UUID authorId,
        String content,
        String topics,
        Instant createdAt
) {
}