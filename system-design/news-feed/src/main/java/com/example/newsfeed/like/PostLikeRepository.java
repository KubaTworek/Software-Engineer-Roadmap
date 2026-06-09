package com.example.newsfeed.like;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {
    long countByIdPostId(UUID postId);
    List<PostLike> findByIdUserIdAndIdPostIdIn(UUID userId, Collection<UUID> postIds);
}
