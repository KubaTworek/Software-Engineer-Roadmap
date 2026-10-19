package pl.jakubtworek.backend.reservation.coordination;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * PostgreSQL pełni rolę koordynatora, lecz lease sam nie jest blokadą poprawności.
 * Każde skuteczne przejęcie zwiększa token, który musi zaakceptować chronione inventory.
 */
@Component
public class PostgresLeaseCoordinator {
    private final JdbcTemplate jdbc;
    public PostgresLeaseCoordinator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public OptionalLong tryAcquire(String name, String owner, Instant now, Duration ttl) {
        String sql = """
                INSERT INTO worker_leases(lease_name, owner_id, lease_until, fencing_token)
                VALUES (?, ?, ?, 1)
                ON CONFLICT (lease_name) DO UPDATE SET
                  owner_id=EXCLUDED.owner_id, lease_until=EXCLUDED.lease_until,
                  fencing_token=worker_leases.fencing_token+1
                WHERE worker_leases.lease_until < ? OR worker_leases.owner_id = ?
                RETURNING fencing_token
                """;
        var tokens = jdbc.query(sql, (row, number) -> row.getLong(1), name, owner,
                Timestamp.from(now.plus(ttl)), Timestamp.from(now), owner);
        return tokens.isEmpty() ? OptionalLong.empty() : OptionalLong.of(tokens.getFirst());
    }
}
