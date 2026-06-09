package com.example.newsfeed.feedinbox;

import com.example.newsfeed.post.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeedInboxRepository extends JpaRepository<FeedInbox, FeedInboxId> {

    @Modifying
    @Query(value = """
            INSERT INTO feed_inbox(user_id, post_id, author_id, source, score, created_at)
            VALUES (:userId, :postId, :authorId, :source, :score, :createdAt)
            ON CONFLICT (user_id, post_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(UUID userId, UUID postId, UUID authorId, String source, double score, Instant createdAt);

    @Modifying
    @Query("DELETE FROM FeedInbox fi WHERE fi.id.postId = :postId")
    int deleteByPostId(UUID postId);

    @Modifying
    @Query("DELETE FROM FeedInbox fi WHERE fi.id.userId = :userId AND fi.authorId = :authorId")
    int deleteByUserIdAndAuthorId(UUID userId, UUID authorId);

    @Query("SELECT p FROM FeedInbox fi JOIN fi.post p JOIN FETCH p.author " +
            "WHERE fi.id.userId = :userId AND p.deletedAt IS NULL " +
            "ORDER BY fi.createdAt DESC, fi.id.postId DESC")
    List<Post> findFirstPage(UUID userId, Pageable pageable);

    @Query("SELECT p FROM FeedInbox fi JOIN fi.post p JOIN FETCH p.author " +
            "WHERE fi.id.userId = :userId AND p.deletedAt IS NULL " +
            "AND (fi.createdAt < :createdAt OR (fi.createdAt = :createdAt AND fi.id.postId < :postId)) " +
            "ORDER BY fi.createdAt DESC, fi.id.postId DESC")
    List<Post> findNextPage(UUID userId, Instant createdAt, UUID postId, Pageable pageable);

    @Query("SELECT fi FROM FeedInbox fi JOIN FETCH fi.post p JOIN FETCH p.author " +
            "WHERE fi.id.userId = :userId AND p.deletedAt IS NULL " +
            "ORDER BY fi.createdAt DESC, fi.id.postId DESC")
    List<FeedInbox> findRecentInboxCandidates(UUID userId, Pageable pageable);
}
