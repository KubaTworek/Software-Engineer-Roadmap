package com.example.newsfeed.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface PostStatsRepository extends JpaRepository<PostStats, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO post_stats(post_id, like_count, comment_count, updated_at)
            VALUES (:postId, :likeCount, :commentCount, :updatedAt)
            ON CONFLICT (post_id)
            DO UPDATE SET
                like_count = EXCLUDED.like_count,
                comment_count = EXCLUDED.comment_count,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(UUID postId, long likeCount, long commentCount, Instant updatedAt);
}
