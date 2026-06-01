package pl.jakubtworek.marketplace.integration.outbox.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEvent;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOutboxEventRepository implements OutboxEventRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<OutboxEvent> rowMapper = (rs, rowNum) -> new OutboxEvent(
            rs.getObject("id", UUID.class),
            rs.getObject("aggregate_id", UUID.class),
            rs.getString("aggregate_type"),
            rs.getString("event_type"),
            rs.getInt("event_version"),
            rs.getString("payload"),
            rs.getObject("correlation_id", UUID.class),
            rs.getObject("causation_id", UUID.class),
            OutboxEventStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            timestampToInstant(rs.getTimestamp("published_at")),
            rs.getInt("retry_count"),
            rs.getString("last_error")
    );

    public JdbcOutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(OutboxEvent event) {
        jdbcTemplate.update("""
                INSERT INTO integration.outbox_events (
                    id, aggregate_id, aggregate_type, event_type, event_version, payload,
                    correlation_id, causation_id, status, created_at, published_at, retry_count, last_error
                ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    published_at = EXCLUDED.published_at,
                    retry_count = EXCLUDED.retry_count,
                    last_error = EXCLUDED.last_error
                """,
                event.id(), event.aggregateId(), event.aggregateType(), event.eventType(), event.eventVersion(), event.payload(),
                event.correlationId(), event.causationId(), event.status().name(), Timestamp.from(event.createdAt()),
                event.publishedAt() == null ? null : Timestamp.from(event.publishedAt()), event.retryCount(), event.lastError()
        );
    }

    @Override
    public Optional<OutboxEvent> findById(UUID eventId) {
        return jdbcTemplate.query("""
                SELECT * FROM integration.outbox_events WHERE id = ?
                """, rowMapper, eventId).stream().findFirst();
    }

    @Override
    public List<OutboxEvent> findAll(int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM integration.outbox_events
                ORDER BY created_at ASC
                LIMIT ?
                """, rowMapper, limit);
    }

    @Override
    public List<OutboxEvent> findByStatus(OutboxEventStatus status, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM integration.outbox_events
                WHERE status = ?
                ORDER BY created_at ASC
                LIMIT ?
                """, rowMapper, status.name(), limit);
    }

    @Override
    public void markPublished(UUID eventId) {
        jdbcTemplate.update("""
                UPDATE integration.outbox_events
                SET status = ?, published_at = ?, last_error = NULL
                WHERE id = ?
                """, OutboxEventStatus.PUBLISHED.name(), Timestamp.from(Instant.now()), eventId);
    }

    @Override
    public void markFailed(UUID eventId, String reason) {
        jdbcTemplate.update("""
                UPDATE integration.outbox_events
                SET status = ?, retry_count = retry_count + 1, last_error = ?
                WHERE id = ?
                """, OutboxEventStatus.FAILED.name(), reason, eventId);
    }

    @Override
    public void markNewForRetry(UUID eventId) {
        jdbcTemplate.update("""
                UPDATE integration.outbox_events
                SET status = ?
                WHERE id = ? AND status <> ?
                """, OutboxEventStatus.NEW.name(), eventId, OutboxEventStatus.PUBLISHED.name());
    }

    private static Instant timestampToInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
