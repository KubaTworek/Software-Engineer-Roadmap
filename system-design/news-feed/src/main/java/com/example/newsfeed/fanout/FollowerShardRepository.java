package com.example.newsfeed.fanout;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FollowerShardRepository extends JpaRepository<FollowerShard, FollowerShardId> {
    List<FollowerShard> findByFolloweeIdAndShardId(UUID followeeId, int shardId, Pageable pageable);

    void deleteByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);

    long countByFolloweeId(UUID followeeId);
}
