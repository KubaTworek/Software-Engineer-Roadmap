package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.key_value;

import java.time.Instant;
import java.util.Set;

/**
 * Przykład wartości przechowywanej w key-value store, np. Redis.
 *
 * Key:
 * session:{sessionId}
 *
 * Value:
 * dane sesji użytkownika.
 */
public class SessionCacheEntry {

    private final String sessionId;
    private final String userId;
    private final Set<String> roles;
    private final Instant createdAt;
    private final Instant expiresAt;

    public SessionCacheEntry(
            String sessionId,
            String userId,
            Set<String> roles,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.roles = Set.copyOf(roles);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static String key(String sessionId) {
        return "session:" + sessionId;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public String sessionId() { return sessionId; }
    public String userId() { return userId; }
    public Set<String> roles() { return roles; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
}
