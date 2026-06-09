package com.example.newsfeed.follow;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class FollowId implements Serializable {
    @Column(name = "follower_id") private UUID followerId;
    @Column(name = "followee_id") private UUID followeeId;
    protected FollowId() {}
    public FollowId(UUID followerId, UUID followeeId) { this.followerId = followerId; this.followeeId = followeeId; }
    public UUID getFollowerId() { return followerId; }
    public UUID getFolloweeId() { return followeeId; }
    public boolean equals(Object o) { return o instanceof FollowId that && Objects.equals(followerId, that.followerId) && Objects.equals(followeeId, that.followeeId); }
    public int hashCode() { return Objects.hash(followerId, followeeId); }
}
