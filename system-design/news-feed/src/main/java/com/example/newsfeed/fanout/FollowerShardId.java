package com.example.newsfeed.fanout;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class FollowerShardId implements Serializable {
    private UUID followeeId;
    private int shardId;
    private UUID followerId;

    public FollowerShardId() {
    }

    public FollowerShardId(UUID followeeId, int shardId, UUID followerId) {
        this.followeeId = followeeId;
        this.shardId = shardId;
        this.followerId = followerId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FollowerShardId that)) {
            return false;
        }
        return shardId == that.shardId
                && Objects.equals(followeeId, that.followeeId)
                && Objects.equals(followerId, that.followerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(followeeId, shardId, followerId);
    }
}
