package com.example.newsfeed.reconciliation;

import com.example.newsfeed.feed.*;
import com.example.newsfeed.follow.FollowRepository;
import com.example.newsfeed.post.*;
import com.example.newsfeed.search.SearchIndexService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Naprawa projekcji nie odtwarza efektów biznesowych. Czyta source of truth i
 * ponownie materializuje wyłącznie dane pochodne, więc jest bezpieczna do retry.
 */
@Service
public class ReconciliationService {
    private final PostRepository posts;
    private final FollowRepository follows;
    private final FeedStorage feed;
    private final FeedCacheService cache;
    private final SearchIndexService search;

    public ReconciliationService(PostRepository posts, FollowRepository follows, FeedStorage feed,
                                 FeedCacheService cache, SearchIndexService search) {
        this.posts = posts; this.follows = follows; this.feed = feed; this.cache = cache; this.search = search;
    }

    @Transactional(readOnly = true)
    public ReconciliationReport rebuildSearch(int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 1_000);
        List<Post> source = posts.findPublishedForRebuild(PageRequest.of(0, limit));
        source.stream().filter(p -> p.getVisibility() == PostVisibility.PUBLIC).forEach(search::indexPost);
        return new ReconciliationReport("search", source.size(), source.size(),
                "Batch ma checkpoint zewnętrzny w produkcji; laboratorium ogranicza pojedynczy przebieg do 1000.");
    }

    @Transactional(readOnly = true)
    public ReconciliationReport rebuildUserFeed(UUID userId, int requestedPerAuthor) {
        int perAuthor = Math.min(Math.max(requestedPerAuthor, 1), 100);
        Set<UUID> authorIds = follows.findFolloweeIds(userId);
        List<Post> candidates = authorIds.stream()
                .flatMap(author -> posts.findLatestPostsByAuthor(author, PageRequest.of(0, perAuthor)).stream())
                .sorted(Comparator.comparing(Post::getCreatedAt).reversed().thenComparing(Post::getId).reversed())
                .toList();
        feed.clearUserFeed(userId);
        feed.appendIdempotent(candidates.stream().map(post -> new FeedInboxItem(userId, post.getId(),
                post.getAuthor().getId(), post.getCreatedAt().toEpochMilli(), "RECONCILIATION", 0,
                post.getCreatedAt())).toList());
        cache.evictPersonalizedFeed(userId);
        return new ReconciliationReport("feed:" + userId, candidates.size(), candidates.size(),
                "Read-time access policy nadal filtruje projekcję po odbudowie.");
    }
}
