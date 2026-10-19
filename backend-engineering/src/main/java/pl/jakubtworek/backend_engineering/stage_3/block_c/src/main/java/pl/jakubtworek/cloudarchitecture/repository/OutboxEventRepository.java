package pl.jakubtworek.cloudarchitecture.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.jakubtworek.cloudarchitecture.entity.OutboxEventEntity;

import java.util.List;
import java.time.Instant;

/**
 * Repository used by the retryable outbox publisher.
 *
 * SKIP LOCKED lets concurrent publishers take different rows. The selected
 * locks live until the surrounding transaction completes, so lock duration is
 * an explicit throughput trade-off of this teaching implementation.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE published_at IS NULL
              AND dead_at IS NULL
              AND next_attempt_at <= :now
            ORDER BY created_at
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockNextPublishableBatch(@Param("now") Instant now);
}
