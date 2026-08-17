package com.example.newsfeed.celebrity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CelebrityPostRepository extends JpaRepository<CelebrityPost, CelebrityPostId> {
    List<CelebrityPost> findByAuthorIdInOrderByCreatedAtDesc(Collection<UUID> authorIds, Pageable pageable);

    void deleteByPostId(UUID postId);
}
