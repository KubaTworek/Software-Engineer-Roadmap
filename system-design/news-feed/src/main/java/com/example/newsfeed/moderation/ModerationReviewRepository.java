package com.example.newsfeed.moderation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface ModerationReviewRepository extends JpaRepository<ModerationReview, UUID> {
    List<ModerationReview> findTop50ByStatusOrderByCreatedAtAsc(String status);
}
