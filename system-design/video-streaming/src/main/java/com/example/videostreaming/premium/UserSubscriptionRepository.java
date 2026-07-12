package com.example.videostreaming.premium;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    @Query("""
           select s from UserSubscription s
           where s.user.id = :userId
             and s.status = com.example.videostreaming.premium.SubscriptionStatus.ACTIVE
             and (s.expiresAt is null or s.expiresAt > :now)
           order by s.startedAt desc
           """)
    List<UserSubscription> findActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    default Optional<UserSubscription> findBestActiveForUser(UUID userId, Instant now) {
        return findActiveForUser(userId, now).stream()
                .max((a, b) -> Integer.compare(a.getPlanCode().level(), b.getPlanCode().level()));
    }
}
