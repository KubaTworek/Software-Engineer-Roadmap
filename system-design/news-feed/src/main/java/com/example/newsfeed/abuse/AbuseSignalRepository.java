package com.example.newsfeed.abuse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;
public interface AbuseSignalRepository extends JpaRepository<AbuseSignal, UUID> {
    @Query("SELECT COALESCE(SUM(a.score), 0) FROM AbuseSignal a WHERE a.userId = :userId")
    double totalScore(UUID userId);
}
