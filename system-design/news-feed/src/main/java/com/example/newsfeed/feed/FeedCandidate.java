package com.example.newsfeed.feed;

import com.example.newsfeed.post.Post;
import com.example.newsfeed.stats.PostStats;

public record FeedCandidate(
        Post post,
        FeedSource source,
        PostStats stats,
        double baseScore
) {
}
