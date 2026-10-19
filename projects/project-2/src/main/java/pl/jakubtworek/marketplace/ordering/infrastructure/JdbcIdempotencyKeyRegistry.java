package pl.jakubtworek.marketplace.ordering.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.ordering.api.IdempotencyKeyRegistry;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Trwały rejestr idempotencji współdzielony przez wszystkie repliki. PostgreSQL
 * advisory transaction lock serializuje tylko żądania o tym samym kluczu. Wpis,
 * agregat Order i outbox powstają w jednej transakcji; wyjątek usuwa także claim,
 * więc klient może bezpiecznie ponowić komendę.
 */
@Component
@Profile("postgres")
public class JdbcIdempotencyKeyRegistry extends IdempotencyKeyRegistry {
    private final JdbcTemplate jdbc;

    public JdbcIdempotencyKeyRegistry(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public Result execute(String key, String fingerprint, Supplier<UUID> operation) {
        if (key == null || key.isBlank()) return new Result(operation.get(), false);
        validateKey(key);

        // hashtextextended + advisory_xact_lock daje stabilny lock w obrębie klastra DB.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, key);
        var existing = jdbc.query("""
                SELECT request_fingerprint, response_id
                FROM ordering.api_idempotency_keys WHERE idempotency_key = ?
                """, (rs, row) -> new Stored(rs.getString(1), rs.getObject(2, UUID.class)), key)
                .stream().findFirst();
        if (existing.isPresent()) {
            if (!existing.get().fingerprint.equals(fingerprint)) throw new IdempotencyKeyConflictException(key);
            return new Result(existing.get().responseId, true);
        }

        UUID responseId = operation.get();
        jdbc.update("""
                INSERT INTO ordering.api_idempotency_keys
                    (idempotency_key, request_fingerprint, response_id, completed_at)
                VALUES (?, ?, ?, now())
                """, key, fingerprint, responseId);
        return new Result(responseId, false);
    }

    private record Stored(String fingerprint, UUID responseId) {}
}
