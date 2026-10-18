package com.example.newsfeed.post;

import com.example.newsfeed.follow.FollowRepository;
import com.example.newsfeed.user.User;
import com.example.newsfeed.user.UserBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ContentAccessPolicyTest {
    private FollowRepository follows;
    private UserBlockRepository blocks;
    private ContentAccessPolicy policy;
    private User author;
    private User viewer;

    @BeforeEach
    void setUp() {
        follows = mock(FollowRepository.class);
        blocks = mock(UserBlockRepository.class);
        policy = new ContentAccessPolicy(follows, blocks);
        author = user("author");
        viewer = user("viewer");
    }

    @Test
    void followerOnlyPostRequiresCurrentRelationshipAtReadTime() {
        Post post = post(PostStatus.PUBLISHED, PostVisibility.FOLLOWERS);
        when(follows.existsByIdFollowerIdAndIdFolloweeId(viewer.getId(), author.getId())).thenReturn(false, true);

        assertThat(policy.canView(viewer, post)).isFalse();
        assertThat(policy.canView(viewer, post)).isTrue();
    }

    @Test
    void blockOverridesPublicVisibilityAndStaleFeedProjection() {
        Post post = post(PostStatus.PUBLISHED, PostVisibility.PUBLIC);
        when(blocks.existsInEitherDirection(viewer.getId(), author.getId())).thenReturn(true);

        assertThat(policy.canView(viewer, post)).isFalse();
    }

    @Test
    void hiddenContentIsNeverReturnedEvenToAuthor() {
        assertThat(policy.canView(author, post(PostStatus.HIDDEN, PostVisibility.PUBLIC))).isFalse();
    }

    private Post post(PostStatus status, PostVisibility visibility) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new Post(UUID.randomUUID(), author, "content", List.of(), now, now, null, status, visibility);
    }

    private static User user(String name) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new User(UUID.randomUUID(), name + "@example.com", name, name, "hash", null, now, now);
    }
}
