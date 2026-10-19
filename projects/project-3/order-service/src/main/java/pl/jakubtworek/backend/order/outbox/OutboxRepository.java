package pl.jakubtworek.backend.order.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {
    /** SKIP LOCKED dzieli batch pomiędzy repliki bez globalnej blokady relaya. */
    @Query(value = "SELECT * FROM outbox_messages WHERE published_at IS NULL AND dead_at IS NULL " +
            "AND (next_attempt_at IS NULL OR next_attempt_at <= :now) " +
            "ORDER BY occurred_at LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxMessage> lockBatchForPublishing(Instant now);
    long countByPublishedAtIsNullAndDeadAtIsNull();
    long countByDeadAtIsNotNull();
}
