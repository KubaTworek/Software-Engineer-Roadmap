package com.example.newsfeed.stats;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface CounterShardRepository extends JpaRepository<CounterShard, CounterShardId> {

    @Modifying
    @Query(value = """
            INSERT INTO counter_shards(entity_type, entity_id, counter_name, shard_id, value, updated_at)
            VALUES (:entityType, :entityId, :counterName, :shardId, :delta, :updatedAt)
            ON CONFLICT (entity_type, entity_id, counter_name, shard_id)
            DO UPDATE SET value = counter_shards.value + :delta, updated_at = :updatedAt
            """, nativeQuery = true)
    void increment(
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("counterName") String counterName,
            @Param("shardId") int shardId,
            @Param("delta") long delta,
            @Param("updatedAt") Instant updatedAt
    );

    @Query(value = """
            SELECT COALESCE(SUM(value), 0)
            FROM counter_shards
            WHERE entity_type = :entityType
              AND entity_id = :entityId
              AND counter_name = :counterName
            """, nativeQuery = true)
    long sum(
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("counterName") String counterName
    );
}
