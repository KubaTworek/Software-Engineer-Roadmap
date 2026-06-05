package com.example.newsfeed.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT p FROM Post p JOIN FETCH p.author " +
            "WHERE p.deletedAt IS NULL " +
            "ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findFirstPage(Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author " +
            "WHERE p.deletedAt IS NULL " +
            "AND (p.createdAt < :createdAt OR (p.createdAt = :createdAt AND p.id < :id)) " +
            "ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findNextPage(Instant createdAt, UUID id, Pageable pageable);
}
