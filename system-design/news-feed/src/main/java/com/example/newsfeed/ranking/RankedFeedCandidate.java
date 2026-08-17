package com.example.newsfeed.ranking;

import com.example.newsfeed.feed.FeedCandidate;

public record RankedFeedCandidate(
        FeedCandidate candidate,
        double score,
        String reason
) {
}
