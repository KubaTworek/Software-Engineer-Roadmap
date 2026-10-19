package pl.jakubtworek.chatsystem.presence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Lease pojedynczego połączenia współdzielony przez wszystkie gatewaye. */
@Entity
@Table(name = "presence_sessions", indexes = @Index(name = "idx_presence_user_expiry", columnList = "user_id, expires_at"))
public class PresenceSession {
    @Id @Column(name = "session_id", length = 160) private String sessionId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "instance_id", nullable = false, length = 120) private String instanceId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    protected PresenceSession() {}
    public PresenceSession(String sessionId, UUID userId, String instanceId, Instant expiresAt) {
        this.sessionId = sessionId; this.userId = userId; this.instanceId = instanceId; this.expiresAt = expiresAt;
    }
    public UUID getUserId() { return userId; }
}
