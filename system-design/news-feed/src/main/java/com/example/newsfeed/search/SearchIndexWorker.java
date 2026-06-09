package com.example.newsfeed.search;

import com.example.newsfeed.events.DomainEvent;
import com.example.newsfeed.events.IdempotentEventProcessor;
import com.example.newsfeed.events.NewsFeedTopics;
import com.example.newsfeed.post.Post;
import com.example.newsfeed.post.PostRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Worker synchronizujący PostgreSQL -> OpenSearch.
 *
 * Dzięki temu tworzenie posta nie zależy bezpośrednio od dostępności OpenSearch.
 *
 * Jeśli OpenSearch chwilowo nie działa, event może zostać ponowiony,
 * a post nadal jest bezpiecznie zapisany w PostgreSQL.
 */
@Service
public class SearchIndexWorker {

    private final IdempotentEventProcessor idempotentEventProcessor;
    private final PostRepository postRepository;
    private final SearchIndexService searchIndexService;

    public SearchIndexWorker(
            IdempotentEventProcessor idempotentEventProcessor,
            PostRepository postRepository,
            SearchIndexService searchIndexService
    ) {
        this.idempotentEventProcessor = idempotentEventProcessor;
        this.postRepository = postRepository;
        this.searchIndexService = searchIndexService;
    }

    @KafkaListener(
            topics = {
                    NewsFeedTopics.POST_CREATED,
                    NewsFeedTopics.POST_DELETED
            },
            groupId = "news-feed-search-index"
    )
    public void onPostEvent(DomainEvent event, Acknowledgment acknowledgment) {
        idempotentEventProcessor.processOnce(event, () -> {
            try {
                if (NewsFeedTopics.POST_CREATED.equals(event.eventType())) {
                    Post post = postRepository.findById(event.entityId())
                            .orElseThrow();

                    searchIndexService.indexPost(post);
                }

                if (NewsFeedTopics.POST_DELETED.equals(event.eventType())) {
                    searchIndexService.deletePost(event.entityId().toString());
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to sync post with OpenSearch.", exception);
            }
        });

        acknowledgment.acknowledge();
    }
}