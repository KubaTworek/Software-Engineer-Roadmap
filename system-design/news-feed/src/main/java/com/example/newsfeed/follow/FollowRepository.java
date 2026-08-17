package com.example.newsfeed.follow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    boolean existsByIdFollowerIdAndIdFolloweeId(UUID followerId, UUID followeeId);

    void deleteByIdFollowerIdAndIdFolloweeId(UUID followerId, UUID followeeId);

    long countByIdFollowerId(UUID followerId);

    long countByIdFolloweeId(UUID followeeId);

    @Query("SELECT f.id.followeeId FROM Follow f WHERE f.id.followerId = :followerId")
    Set<UUID> findFolloweeIds(UUID followerId);

    @Query("SELECT f.id.followerId FROM Follow f WHERE f.id.followeeId = :followeeId")
    Set<UUID> findFollowerIds(UUID followeeId);
}
