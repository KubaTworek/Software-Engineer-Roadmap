package pl.jakubtworek.chatsystem.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
            select * from outbox_events
            where status in ('NEW','FAILED') and attempts < :maxAttempts
              and (next_attempt_at is null or next_attempt_at <= CURRENT_TIMESTAMP)
            order by created_at asc limit :batchSize for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockPublishable(@Param("maxAttempts") int maxAttempts, @Param("batchSize") int batchSize);

    @Query("select e from OutboxEvent e where e.status = pl.jakubtworek.chatsystem.outbox.OutboxStatus.ENQUEUED and e.claimedAt < :threshold")
    List<OutboxEvent> findExpiredClaims(@Param("threshold") java.time.Instant threshold);

    long countByStatus(OutboxStatus status);
}
