package com.example.newsfeed.moderation;

import com.example.newsfeed.events.NewsFeedTopics;
import com.example.newsfeed.feed.FeedCacheService;
import com.example.newsfeed.outbox.DomainEventPublisher;
import com.example.newsfeed.post.*;
import com.example.newsfeed.user.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ModerationServiceTest {
    @Test
    void uppercaseSignalIsComputedBeforeCaseNormalization() {
        ModerationService service = new ModerationService(mock(ModerationReviewRepository.class), 0.85, 0.05);

        ModerationDecision decision = service.evaluateText("THIS IS LOUD");

        assertThat(decision.status()).isEqualTo("needs_review");
        assertThat(decision.score()).isEqualTo(0.10);
    }

    @Test
    void moderatorApprovalPublishesPendingPostAndWritesAuditInOneUseCase() {
        ModerationReviewRepository reviews = mock(ModerationReviewRepository.class);
        PostRepository posts = mock(PostRepository.class);
        ModerationAuditLogRepository audit = mock(ModerationAuditLogRepository.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        FeedCacheService cache = mock(FeedCacheService.class);
        Instant now = Instant.parse("2026-02-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        User author = user("author", UserRole.USER);
        User moderator = user("moderator", UserRole.MODERATOR);
        Post post = new Post(UUID.randomUUID(), author, "review me", List.of(), now.minusSeconds(60),
                now.minusSeconds(60), null, PostStatus.PENDING_REVIEW, PostVisibility.PUBLIC);
        ModerationReview review = new ModerationReview(UUID.randomUUID(), "post", post.getId(), "needs_review",
                0.7, "classifier", null, now.minusSeconds(30), null);
        when(reviews.findByIdForDecision(review.getId())).thenReturn(Optional.of(review));
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        ModerationService service = new ModerationService(reviews, 0.85, 0.60, posts, audit, events, cache, clock);

        service.decide(moderator, review.getId(),
                new ModerationDecisionRequest(ModerationDecisionRequest.Decision.APPROVE, "context verified"));

        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(review.getStatus()).isEqualTo("approved");
        assertThat(review.getReviewerId()).isEqualTo(moderator.getId());
        verify(audit).save(any(ModerationAuditLog.class));
        verify(events).publish(eq(NewsFeedTopics.POST_CREATED), eq(author.getId().toString()), any());
    }

    private static User user(String name, UserRole role) {
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        return new User(UUID.randomUUID(), name + "@example.com", name, name, "hash", null, at, at, role);
    }
}
