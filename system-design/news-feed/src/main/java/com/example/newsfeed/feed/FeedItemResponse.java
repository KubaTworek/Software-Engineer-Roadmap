package com.example.newsfeed.feed;

import com.example.newsfeed.post.PostResponse;

public record FeedItemResponse(
        PostResponse post,
        FeedSource source,
        double score,
        String reason
) {
}
