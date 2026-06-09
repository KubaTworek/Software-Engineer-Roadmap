package com.example.newsfeed.follow;
import java.time.Instant;
import java.util.UUID;
public record FollowResponse(UUID followerId, UUID followeeId, Instant createdAt) {
    public static FollowResponse from(Follow follow) { return new FollowResponse(follow.getFollower().getId(), follow.getFollowee().getId(), follow.getCreatedAt()); }
}
