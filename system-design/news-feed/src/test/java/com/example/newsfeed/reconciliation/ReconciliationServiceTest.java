package com.example.newsfeed.reconciliation;

import com.example.newsfeed.feed.*;
import com.example.newsfeed.follow.FollowRepository;
import com.example.newsfeed.post.*;
import com.example.newsfeed.search.SearchIndexService;
import com.example.newsfeed.user.User;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReconciliationServiceTest {
    @Test
    void feedRebuildClearsOnlyTargetProjectionAndReplaysSourceOfTruth() {
        PostRepository posts = mock(PostRepository.class);
        FollowRepository follows = mock(FollowRepository.class);
        FeedStorage feed = mock(FeedStorage.class);
        FeedCacheService cache = mock(FeedCacheService.class);
        SearchIndexService search = mock(SearchIndexService.class);
        UUID viewer = UUID.randomUUID();
        User author = new User(UUID.randomUUID(), "a@x.pl", "a", "A", "h", null,
                Instant.EPOCH, Instant.EPOCH);
        Post post = new Post(UUID.randomUUID(), author, "content", List.of(), Instant.EPOCH, Instant.EPOCH,
                null, PostStatus.PUBLISHED, PostVisibility.PUBLIC);
        when(follows.findFolloweeIds(viewer)).thenReturn(Set.of(author.getId()));
        when(posts.findLatestPostsByAuthor(eq(author.getId()), any())).thenReturn(List.of(post));
        ReconciliationService service = new ReconciliationService(posts, follows, feed, cache, search);

        ReconciliationReport result = service.rebuildUserFeed(viewer, 50);

        assertThat(result.repaired()).isOne();
        verify(feed).clearUserFeed(viewer);
        verify(feed).appendIdempotent(argThat(items -> items.size() == 1));
        verify(cache).evictPersonalizedFeed(viewer);
    }
}
