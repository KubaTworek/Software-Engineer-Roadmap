package com.example.newsfeed.fanout;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "follower_shards")
@IdClass(FollowerShardId.class)
public class FollowerShard {

    @Id
    private UUID followeeId;

    @Id
    private int shardId;

    @Id
    private UUID followerId;

    @Column(nullable = false)
    private Instant createdAt;

    protected FollowerShard() {
    }

    public FollowerShard(UUID followeeId, int shardId, UUID followerId, Instant createdAt) {
        this.followeeId = followeeId;
        this.shardId = shardId;
        this.followerId = followerId;
        this.createdAt = createdAt;
    }

    public UUID getFollowerId() {
        return followerId;
    }

    public int getShardId() {
        return shardId;
    }
}
