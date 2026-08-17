package com.example.newsfeed.follow;

import com.example.newsfeed.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "follows")
public class Follow {
    @EmbeddedId private FollowId id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("followerId") @JoinColumn(name = "follower_id") private User follower;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("followeeId") @JoinColumn(name = "followee_id") private User followee;
    @Column(nullable = false) private Instant createdAt;
    protected Follow() {}
    public Follow(FollowId id, User follower, User followee, Instant createdAt) { this.id = id; this.follower = follower; this.followee = followee; this.createdAt = createdAt; }
    public User getFollower() { return follower; }
    public User getFollowee() { return followee; }
    public Instant getCreatedAt() { return createdAt; }
}
