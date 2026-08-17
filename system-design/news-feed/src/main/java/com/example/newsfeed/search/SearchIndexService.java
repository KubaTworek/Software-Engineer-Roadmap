package com.example.newsfeed.search;

import com.example.newsfeed.post.Post;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;

/**
 * Serwis odpowiedzialny za synchronizację postów z indeksem OpenSearch.
 *
 * Postgres jest źródłem prawdy.
 * OpenSearch jest indeksem wyszukiwawczym.
 *
 * Po utworzeniu posta:
 * - zapisujemy post w bazie,
 * - indeksujemy dokument w OpenSearch.
 *
 * Po usunięciu posta:
 * - oznaczamy post jako deleted w bazie,
 * - usuwamy dokument z OpenSearch.
 */
@Service
public class SearchIndexService {

    private final OpenSearchClient client;
    private final String indexName;

    public SearchIndexService(
            OpenSearchClient client,
            @Value("${newsfeed.search.index:newsfeed_posts}") String indexName
    ) {
        this.client = client;
        this.indexName = indexName;
    }

    /**
     * Tworzy indeks przy starcie aplikacji, jeśli jeszcze nie istnieje.
     *
     * W produkcji często robi się to migracjami infrastruktury,
     * np. Terraformem albo osobnym jobem deployowym.
     *
     * W aplikacji demo jest to wygodne, bo indeks powstaje automatycznie.
     */
    @PostConstruct
    public void ensureIndexExists() throws IOException {
        BooleanResponse exists = client.indices().exists(
                ExistsRequest.of(e -> e.index(indexName))
        );

        if (exists.value()) {
            return;
        }

        client.indices().create(
                CreateIndexRequest.of(c -> c
                        .index(indexName)
                        .mappings(m -> m
                                .properties("postId", p -> p.keyword(k -> k))
                                .properties("authorId", p -> p.keyword(k -> k))
                                .properties("content", p -> p.text(t -> t))
                                .properties("topics", p -> p.text(t -> t))
                                .properties("createdAt", p -> p.date(d -> d))
                        )
                )
        );
    }

    /**
     * Indeksuje post w OpenSearch.
     *
     * ID dokumentu = postId.
     *
     * Dzięki temu ponowne indeksowanie tego samego posta jest idempotentne:
     * dokument zostanie nadpisany, a nie zdublowany.
     */
    public void indexPost(Post post) {
        PostSearchDocument document = new PostSearchDocument(
                post.getId(),
                post.getAuthor().getId(),
                post.getContent(),
                post.getTopics() == null ? "" : String.join(" ", post.getTopics()),
                post.getCreatedAt()
        );

        try {
            client.index(i -> i
                    .index(indexName)
                    .id(post.getId().toString())
                    .document(document)
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Usuwa dokument posta z OpenSearch.
     *
     * Wywoływane po usunięciu posta.
     *
     * Dzięki temu usunięty post nie będzie już zwracany przez search.
     */
    public void deletePost(String postId) {
        try {
            client.delete(d -> d
                    .index(indexName)
                    .id(postId)
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}