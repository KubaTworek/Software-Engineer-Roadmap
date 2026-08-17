package com.example.newsfeed.feed;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "newsfeed.feed.storage",
        havingValue = "postgres",
        matchIfMissing = true
)
public class PostgresFeedStorage implements FeedStorage {

    private final JpaFeedInboxRepository repository;

    public PostgresFeedStorage(JpaFeedInboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "feedStorage")
    public void appendIdempotent(Collection<FeedInboxItem> items) {
        for (FeedInboxItem item : items) {
            repository.insertIgnore(
                    item.userId(),
                    item.postId(),
                    item.authorId(),
                    item.score(),
                    item.source(),
                    item.shardId(),
                    item.createdAt()
            );
        }
    }

    @Override
    @Transactional
    public void removePost(UUID postId) {
        repository.deleteByPostId(postId);
    }

    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "feedStorage")
    public List<UUID> getPostIds(UUID userId, Instant beforeCreatedAt, UUID beforePostId, int limit) {
        List<JpaFeedInboxEntity> rows = beforeCreatedAt == null || beforePostId == null
                ? repository.firstPage(userId, PageRequest.of(0, limit))
                : repository.nextPage(userId, beforeCreatedAt, beforePostId, PageRequest.of(0, limit));

        return rows.stream().map(JpaFeedInboxEntity::getPostId).toList();
    }
}
