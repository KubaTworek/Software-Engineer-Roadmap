package com.example.newsfeed.fanout;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FollowerShardRepository extends JpaRepository<FollowerShard, FollowerShardId> {
    List<FollowerShard> findByFolloweeIdAndShardId(UUID followeeId, int shardId, Pageable pageable);

    @Query("""
            SELECT f FROM FollowerShard f
            WHERE f.followeeId = :followeeId AND f.shardId = :shardId
              AND (:afterFollowerId IS NULL OR f.followerId > :afterFollowerId)
            ORDER BY f.followerId ASC
            """)
    List<FollowerShard> findPage(UUID followeeId, int shardId, UUID afterFollowerId, Pageable pageable);

    void deleteByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);

    long countByFolloweeId(UUID followeeId);
}
