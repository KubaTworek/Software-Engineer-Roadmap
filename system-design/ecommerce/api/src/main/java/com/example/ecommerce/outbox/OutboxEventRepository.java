package com.example.ecommerce.outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop50ByStatusOrderByIdAsc(OutboxEventStatus status);

    /**
     * PostgreSQL SKIP LOCKED pozwala wielu instancjom workera dzielić backlog:
     * zablokowany rekord jest pomijany zamiast zatrzymywać cały batch. To nie
     * zapewnia exactly-once; awaria po publikacji nadal może utworzyć duplikat.
     */
    @Query(value = """
            select * from outbox_events
            where status = 'NEW'
               or (status = 'FAILED' and next_attempt_at <= :now)
               or (status = 'PROCESSING' and claimed_at < :staleBefore)
            order by id
            for update skip locked
            limit :batchSize
            """, nativeQuery = true)
    List<OutboxEvent> claimPublishableBatch(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("batchSize") int batchSize);

    long countByStatus(OutboxEventStatus status);

    java.util.Optional<OutboxEvent> findFirstByStatusInOrderByCreatedAtAsc(
            java.util.Collection<OutboxEventStatus> statuses);
}
