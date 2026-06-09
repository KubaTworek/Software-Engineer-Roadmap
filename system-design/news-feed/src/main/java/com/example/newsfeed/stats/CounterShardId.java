package com.example.newsfeed.stats;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CounterShardId implements Serializable {
    private String entityType;
    private UUID entityId;
    private String counterName;
    private int shardId;

    public CounterShardId() {
    }

    public CounterShardId(String entityType, UUID entityId, String counterName, int shardId) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.counterName = counterName;
        this.shardId = shardId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CounterShardId that)) {
            return false;
        }
        return shardId == that.shardId
                && Objects.equals(entityType, that.entityType)
                && Objects.equals(entityId, that.entityId)
                && Objects.equals(counterName, that.counterName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, entityId, counterName, shardId);
    }
}
