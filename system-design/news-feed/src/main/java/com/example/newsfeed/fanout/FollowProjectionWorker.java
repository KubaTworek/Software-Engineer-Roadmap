package com.example.newsfeed.fanout;

import com.example.newsfeed.events.DomainEvent;
import com.example.newsfeed.events.IdempotentEventProcessor;
import com.example.newsfeed.events.NewsFeedTopics;
import com.example.newsfeed.feed.FeedInboxItem;
import com.example.newsfeed.feed.FeedStorage;
import com.example.newsfeed.post.Post;
import com.example.newsfeed.post.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Materializuje konsekwencje follow/unfollow poza transakcją requestu. */
@Service
public class FollowProjectionWorker {
    private final IdempotentEventProcessor events;
    private final FeedStorage inbox;
    private final PostRepository posts;
    private final int backfillLimit;

    public FollowProjectionWorker(IdempotentEventProcessor events, FeedStorage inbox, PostRepository posts,
                                  @Value("${newsfeed.workers.fanout.follow-backfill-limit:50}") int backfillLimit) {
        this.events = events;
        this.inbox = inbox;
        this.posts = posts;
        this.backfillLimit = backfillLimit;
    }

    @KafkaListener(topics = {NewsFeedTopics.FOLLOW_CREATED, NewsFeedTopics.FOLLOW_DELETED},
            groupId = "news-feed-follow-projection")
    public void handle(DomainEvent event, Acknowledgment acknowledgment) {
        events.processOnce("follow-projection", event, () -> {
            UUID follower = UUID.fromString(event.attributes().get("followerId"));
            UUID followee = UUID.fromString(event.attributes().get("followeeId"));
            if (NewsFeedTopics.FOLLOW_CREATED.equals(event.eventType())) {
                java.util.List<Post> recent = posts.findLatestPostsByAuthor(
                        followee, PageRequest.of(0, backfillLimit));
                inbox.appendIdempotent(recent.stream()
                        .map(post -> new FeedInboxItem(follower, post.getId(), followee,
                                post.getCreatedAt().toEpochMilli(), "FOLLOW_BACKFILL", 0, post.getCreatedAt()))
                        .toList());
            } else {
                inbox.removeAuthorFromUserFeed(follower, followee);
            }
        });
        acknowledgment.acknowledge();
    }
}
