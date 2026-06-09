package com.example.newsfeed.stats;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "counter_shards")
@IdClass(CounterShardId.class)
public class CounterShard {

    @Id
    private String entityType;

    @Id
    private UUID entityId;

    @Id
    private String counterName;

    @Id
    private int shardId;

    @Column(nullable = false)
    private long value;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CounterShard() {
    }

    public CounterShard(String entityType, UUID entityId, String counterName, int shardId, long value, Instant updatedAt) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.counterName = counterName;
        this.shardId = shardId;
        this.value = value;
        this.updatedAt = updatedAt;
    }
}
