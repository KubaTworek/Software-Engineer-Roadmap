package pl.jakubtworek.chatsystem.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query("""
            select e from OutboxEvent e
            where e.status in (pl.jakubtworek.chatsystem.outbox.OutboxStatus.NEW,
                               pl.jakubtworek.chatsystem.outbox.OutboxStatus.FAILED)
              and e.attempts < :maxAttempts
            order by e.createdAt asc
            """)
    List<OutboxEvent> findPublishable(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    long countByStatus(OutboxStatus status);
}
