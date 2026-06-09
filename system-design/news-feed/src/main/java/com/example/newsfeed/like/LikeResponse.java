package com.example.newsfeed.like;
import java.util.UUID;
public record LikeResponse(UUID postId, boolean liked, long likeCount) {}
