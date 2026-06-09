package com.example.newsfeed.ranking;

import com.example.newsfeed.feed.FeedCandidate;
import com.example.newsfeed.feed.FeedSource;
import com.example.newsfeed.follow.FollowRepository;
import com.example.newsfeed.like.PostLike;
import com.example.newsfeed.like.PostLikeRepository;
import com.example.newsfeed.post.Post;
import com.example.newsfeed.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RankingService {

    private final FollowRepository followRepository;
    private final PostLikeRepository postLikeRepository;

    public RankingService(FollowRepository followRepository, PostLikeRepository postLikeRepository) {
        this.followRepository = followRepository;
        this.postLikeRepository = postLikeRepository;
    }

    @Transactional(readOnly = true)
    public List<RankedFeedCandidate> rank(User user, List<FeedCandidate> candidates) {
        Set<UUID> followedAuthorIds = followRepository.findFolloweeIds(user.getId());
        Set<String> preferredTopics = inferPreferredTopics(user.getId(), candidates);
        Instant now = Instant.now();

        return candidates.stream()
                .map(candidate -> rankOne(candidate, followedAuthorIds, preferredTopics, now))
                .sorted(Comparator.comparingDouble(RankedFeedCandidate::score).reversed())
                .toList();
    }

    private RankedFeedCandidate rankOne(
            FeedCandidate candidate,
            Set<UUID> followedAuthorIds,
            Set<String> preferredTopics,
            Instant now
    ) {
        Post post = candidate.post();

        double freshness = freshnessScore(post.getCreatedAt(), now);
        double relationship = relationshipScore(post, followedAuthorIds, candidate.source());
        double popularity = popularityScore(candidate);
        double topic = topicScore(post, preferredTopics);
        double sourceBoost = sourceBoost(candidate.source());

        double score =
                0.40 * freshness +
                0.25 * relationship +
                0.20 * popularity +
                0.10 * topic +
                0.05 * sourceBoost +
                candidate.baseScore();

        return new RankedFeedCandidate(candidate, score, reason(candidate.source(), relationship, popularity, topic));
    }

    private double freshnessScore(Instant createdAt, Instant now) {
        long ageHours = Math.max(0, Duration.between(createdAt, now).toHours());
        return Math.exp(-ageHours / 36.0);
    }

    private double relationshipScore(Post post, Set<UUID> followedAuthorIds, FeedSource source) {
        if (source == FeedSource.FOLLOWING || followedAuthorIds.contains(post.getAuthor().getId())) {
            return 1.0;
        }
        return 0.15;
    }

    private double popularityScore(FeedCandidate candidate) {
        if (candidate.stats() == null) {
            return 0.0;
        }
        long engagement = candidate.stats().getLikeCount() + 2 * candidate.stats().getCommentCount();
        return Math.min(1.0, Math.log10(engagement + 1) / 3.0);
    }

    private double topicScore(Post post, Set<String> preferredTopics) {
        if (preferredTopics.isEmpty() || post.getTopics().isEmpty()) {
            return 0.0;
        }

        long matches = post.getTopics().stream()
                .filter(preferredTopics::contains)
                .count();

        return Math.min(1.0, matches / 3.0);
    }

    private double sourceBoost(FeedSource source) {
        return switch (source) {
            case FOLLOWING -> 1.0;
            case TRENDING -> 0.45;
            case RECOMMENDED -> 0.35;
        };
    }

    private String reason(FeedSource source, double relationship, double popularity, double topic) {
        if (source == FeedSource.FOLLOWING || relationship > 0.9) {
            return "Because you follow this author";
        }
        if (popularity > 0.5) {
            return "Trending in the community";
        }
        if (topic > 0.0) {
            return "Recommended from topics you engage with";
        }
        return "Recommended for you";
    }

    private Set<String> inferPreferredTopics(UUID userId, List<FeedCandidate> candidates) {
        Set<UUID> postIds = candidates.stream()
                .map(candidate -> candidate.post().getId())
                .collect(Collectors.toSet());

        if (postIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> likedPostIds = postLikeRepository.findByIdUserIdAndIdPostIdIn(userId, postIds)
                .stream()
                .map(PostLike::getId)
                .map(id -> id.getPostId())
                .collect(Collectors.toSet());

        Set<String> topics = new HashSet<>();
        for (FeedCandidate candidate : candidates) {
            boolean ownPost = candidate.post().getAuthor().getId().equals(userId);
            boolean liked = likedPostIds.contains(candidate.post().getId());
            if (ownPost || liked) {
                topics.addAll(candidate.post().getTopics());
            }
        }
        return topics;
    }
}
