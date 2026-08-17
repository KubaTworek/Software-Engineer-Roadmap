package com.example.newsfeed.feed;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaFeedInboxRepository extends JpaRepository<JpaFeedInboxEntity, JpaFeedInboxId> {

    @Modifying
    @Query(value = """
            INSERT INTO feed_inbox(user_id, post_id, author_id, score, source, shard_id, created_at)
            VALUES (:userId, :postId, :authorId, :score, :source, :shardId, :createdAt)
            ON CONFLICT (user_id, post_id) DO NOTHING
            """, nativeQuery = true)
    void insertIgnore(
            @Param("userId") UUID userId,
            @Param("postId") UUID postId,
            @Param("authorId") UUID authorId,
            @Param("score") double score,
            @Param("source") String source,
            @Param("shardId") int shardId,
            @Param("createdAt") Instant createdAt
    );

    @Modifying
    void deleteByPostId(UUID postId);

    @Query("SELECT f FROM JpaFeedInboxEntity f " +
            "WHERE f.userId = :userId " +
            "ORDER BY f.createdAt DESC, f.postId DESC")
    List<JpaFeedInboxEntity> firstPage(UUID userId, Pageable pageable);

    @Query("SELECT f FROM JpaFeedInboxEntity f " +
            "WHERE f.userId = :userId " +
            "AND (f.createdAt < :createdAt OR (f.createdAt = :createdAt AND f.postId < :postId)) " +
            "ORDER BY f.createdAt DESC, f.postId DESC")
    List<JpaFeedInboxEntity> nextPage(UUID userId, Instant createdAt, UUID postId, Pageable pageable);
}
