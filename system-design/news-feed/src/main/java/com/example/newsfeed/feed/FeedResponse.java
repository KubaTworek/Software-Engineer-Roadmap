package com.example.newsfeed.feed;

import com.example.newsfeed.post.PostResponse;

import java.util.List;

public record FeedResponse(
        List<PostResponse> items,
        String nextCursor
) {
}
