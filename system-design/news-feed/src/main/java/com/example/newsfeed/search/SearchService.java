package com.example.newsfeed.search;

import com.example.newsfeed.post.Post;
import com.example.newsfeed.post.PostRepository;
import com.example.newsfeed.post.PostResponse;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Produkcyjna wersja SearchService oparta o OpenSearch.
 *
 * Flow:
 * 1. API dostaje query użytkownika,
 * 2. SearchService wysyła query do OpenSearch,
 * 3. OpenSearch zwraca trafione dokumenty w kolejności relevance score,
 * 4. z dokumentów bierzemy postId,
 * 5. PostRepository hydratuje pełne posty z PostgreSQL,
 * 6. zachowujemy kolejność wyników z OpenSearch,
 * 7. zwracamy PostResponse.
 *
 * Ważne:
 * OpenSearch służy do wyszukiwania.
 * PostgreSQL dalej jest źródłem prawdy dla encji Post.
 */
@Service
public class SearchService {

    private final OpenSearchClient client;
    private final PostRepository postRepository;
    private final String indexName;

    public SearchService(
            OpenSearchClient client,
            PostRepository postRepository,
            @Value("${newsfeed.search.index:newsfeed_posts}") String indexName
    ) {
        this.client = client;
        this.postRepository = postRepository;
        this.indexName = indexName;
    }

    /**
     * Wyszukuje posty po treści i topicach.
     *
     * To zastępuje lokalne filtrowanie:
     *
     * postRepository.findFirstGlobalPage(...).stream().filter(...)
     *
     * Produkcyjnie nie chcemy filtrować w Javie,
     * bo wtedy przeszukujemy tylko mały wycinek danych.
     *
     * OpenSearch przeszukuje cały indeks i zwraca najlepsze dopasowania.
     */
    @Transactional(readOnly = true)
    public List<PostResponse> search(String q, int limit) {
        String query = q == null ? "" : q.trim();

        if (query.isBlank()) {
            return List.of();
        }

        int safeLimit = Math.min(Math.max(limit, 1), 50);

        try {
            /*
             * Query do OpenSearch.
             *
             * multi_match szuka tej samej frazy w wielu polach:
             * - content,
             * - topics.
             *
             * content ma większą wagę, bo treść posta jest ważniejsza
             * niż same tagi/topic.
             */
            SearchResponse<PostSearchDocument> response = client.search(s -> s
                            .index(indexName)
                            .size(safeLimit)
                            .query(qb -> qb
                                    .multiMatch(mm -> mm
                                            .query(query)
                                            .fields("content^2", "topics")
                                    )
                            ),
                    PostSearchDocument.class
            );

            /*
             * Wyciągamy postId z dokumentów OpenSearch.
             *
             * Kolejność ID jest kolejnością trafności z OpenSearch.
             */
            List<UUID> ids = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(PostSearchDocument::postId)
                    .toList();

            if (ids.isEmpty()) {
                return List.of();
            }

            /*
             * Hydratujemy pełne posty z bazy.
             *
             * OpenSearch nie powinien być źródłem prawdy dla pełnego modelu domeny.
             */
            List<Post> posts = postRepository.findFeedPostsByIds(ids);

            /*
             * findFeedPostsByIds może nie zachować kolejności ID.
             *
             * Dlatego budujemy mapę i odtwarzamy kolejność zgodną z OpenSearch.
             */
            Map<UUID, Post> postsById = posts.stream()
                    .collect(Collectors.toMap(Post::getId, Function.identity()));

            return ids.stream()
                    .map(postsById::get)

                    /*
                     * Post może już nie istnieć albo być soft-deleted.
                     * Wtedy nie zwracamy go w wynikach.
                     */
                    .filter(Objects::nonNull)
                    .map(PostResponse::from)
                    .toList();

        } catch (Exception exception) {
            /*
             * Fallback awaryjny.
             *
             * W produkcji możesz tu:
             * - zwrócić pustą listę,
             * - użyć prostego fallbacku DB,
             * - albo rzucić wyjątek 503.
             *
             * Najbezpieczniej nie udawać pełnego searcha,
             * jeśli OpenSearch jest niedostępny.
             */
            throw new IllegalStateException("Search is temporarily unavailable.", exception);
        }
    }
}