package com.example.newsfeed.moderation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List; import java.util.UUID;
public interface ModerationReviewRepository extends JpaRepository<ModerationReview, UUID> {
    List<ModerationReview> findTop50ByStatusOrderByCreatedAtAsc(String status);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ModerationReview r WHERE r.id = :id")
    java.util.Optional<ModerationReview> findByIdForDecision(UUID id);
}
