package com.example.ratelimiter.usage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Durable receipt + outbox boundary. A receipt and its publication intent are
 * committed in one PostgreSQL transaction. This closes the crash window of a
 * direct Kafka send, but does not make the limiter the business billing source:
 * final settlement still reconciles with the system that performed the action.
 */
public class JdbcUsageOutbox {
    private final DataSource dataSource;

    public JdbcUsageOutbox(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Duplicate event IDs are idempotent and do not double-charge usage. */
    public boolean record(UsageReceipt receipt) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int inserted;
                try (var statement = connection.prepareStatement("""
                        INSERT INTO rate_limiter_usage_receipt
                          (event_id, tenant_id, operation_cost, occurred_at, payload)
                        VALUES (?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING
                        """)) {
                    statement.setObject(1, receipt.eventId());
                    statement.setString(2, receipt.tenantId());
                    statement.setLong(3, receipt.operationCost());
                    statement.setObject(4, java.sql.Timestamp.from(receipt.occurredAt()));
                    statement.setString(5, receipt.payload());
                    inserted = statement.executeUpdate();
                }
                if (inserted == 1) {
                    try (var statement = connection.prepareStatement(
                            "INSERT INTO rate_limiter_usage_outbox(event_id) VALUES (?)")) {
                        statement.setObject(1, receipt.eventId());
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return inserted == 1;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot atomically store usage receipt and outbox", exception);
        }
    }

    /**
     * Claims work with SKIP LOCKED and a lease. A crashed relay does not lose the
     * event: another owner can reclaim it after leaseUntil. Kafka may still see
     * a duplicate after publish-before-mark; consumers must deduplicate eventId.
     */
    public List<ClaimedUsage> claim(String owner, int limit, Instant now, Duration lease) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            List<ClaimedUsage> claimed = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    WITH candidates AS (
                      SELECT event_id FROM rate_limiter_usage_outbox
                      WHERE attempts < ? AND (state = 'PENDING' OR (state = 'IN_FLIGHT' AND lease_until < ?))
                      ORDER BY created_at, event_id FOR UPDATE SKIP LOCKED LIMIT ?
                    )
                    UPDATE rate_limiter_usage_outbox o
                    SET state='IN_FLIGHT', owner=?, lease_until=?, attempts=attempts+1
                    FROM candidates c, rate_limiter_usage_receipt r
                    WHERE o.event_id=c.event_id AND r.event_id=o.event_id
                    RETURNING o.event_id, r.tenant_id, r.operation_cost, r.occurred_at,
                              r.payload, o.attempts
                    """)) {
                statement.setInt(1, 10);
                statement.setObject(2, java.sql.Timestamp.from(now));
                statement.setInt(3, limit);
                statement.setString(4, owner);
                statement.setObject(5, java.sql.Timestamp.from(now.plus(lease)));
                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        claimed.add(new ClaimedUsage((UUID) result.getObject(1), result.getString(2),
                                result.getLong(3), result.getTimestamp(4).toInstant(), result.getString(5),
                                result.getInt(6)));
                    }
                }
            }
            connection.commit();
            return List.copyOf(claimed);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot claim usage outbox", exception);
        }
    }

    public boolean markPublished(UUID eventId, String owner, Instant publishedAt) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     UPDATE rate_limiter_usage_outbox
                     SET state='PUBLISHED', published_at=?, lease_until=NULL
                     WHERE event_id=? AND owner=? AND state='IN_FLIGHT'
                     """)) {
            statement.setObject(1, java.sql.Timestamp.from(publishedAt));
            statement.setObject(2, eventId);
            statement.setString(3, owner);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot acknowledge usage outbox", exception);
        }
    }

    /** Stops a poison event after bounded retries without blocking later rows. */
    public boolean markDead(UUID eventId, String owner, String error) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     UPDATE rate_limiter_usage_outbox
                     SET state='DEAD', lease_until=NULL, last_error=?
                     WHERE event_id=? AND owner=? AND state='IN_FLIGHT'
                     """)) {
            statement.setString(1, error == null ? "unknown" : error.substring(0, Math.min(500, error.length())));
            statement.setObject(2, eventId);
            statement.setString(3, owner);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot dead-letter usage event", exception);
        }
    }

    /** Source-side total used by reconciliation against a downstream projection. */
    public long authoritativeUsage(String tenantId, Instant from, Instant to) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT COALESCE(SUM(operation_cost), 0) FROM rate_limiter_usage_receipt
                     WHERE tenant_id=? AND occurred_at>=? AND occurred_at<?
                     """)) {
            statement.setString(1, tenantId);
            statement.setObject(2, java.sql.Timestamp.from(from));
            statement.setObject(3, java.sql.Timestamp.from(to));
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot reconcile usage", exception);
        }
    }

    public record UsageReceipt(UUID eventId, String tenantId, long operationCost,
                               Instant occurredAt, String payload) {
        public UsageReceipt {
            if (eventId == null || tenantId == null || tenantId.isBlank() || operationCost <= 0
                    || occurredAt == null || payload == null) {
                throw new IllegalArgumentException("Complete positive usage receipt is required");
            }
        }
    }

    public record ClaimedUsage(UUID eventId, String tenantId, long operationCost,
                               Instant occurredAt, String payload, int attempt) {}
}
