package com.example.newsfeed.comment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);
    long countByPostIdAndDeletedAtIsNull(UUID postId);
    @Query("SELECT c.post.id, COUNT(c.id) FROM Comment c WHERE c.deletedAt IS NULL AND c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIds(Collection<UUID> postIds);
    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.deletedAt IS NULL AND c.post.id = :postId ORDER BY c.createdAt ASC, c.id ASC")
    List<Comment> findFirstPage(UUID postId, Pageable pageable);
    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.deletedAt IS NULL AND c.post.id = :postId AND (c.createdAt > :createdAt OR (c.createdAt = :createdAt AND c.id > :id)) ORDER BY c.createdAt ASC, c.id ASC")
    List<Comment> findNextPage(UUID postId, Instant createdAt, UUID id, Pageable pageable);
}
