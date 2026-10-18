package pl.jakubtworek.chatsystem.realtime;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Jednorazowe capability do handshake'u przeglądarkowego. W bazie zapisujemy wyłącznie hash,
 * więc odczyt tabeli nie daje gotowych poświadczeń. Ticket nie zastępuje sesji użytkownika:
 * jest krótko żyjącym mostem pomiędzy uwierzytelnionym REST a WebSocketem.
 */
@Entity
@Table(name = "websocket_tickets")
public class WebSocketTicket {
    @Id @Column(name = "token_hash", length = 64) private String tokenHash;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 80) private String username;
    @Column(length = 300) private String origin;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    protected WebSocketTicket() {}
    public WebSocketTicket(String tokenHash, UUID userId, String username, String origin, Instant expiresAt) {
        this.tokenHash = tokenHash; this.userId = userId; this.username = username;
        this.origin = origin; this.expiresAt = expiresAt;
    }
    public boolean canConsume(Instant now, String requestOrigin) {
        return consumedAt == null && now.isBefore(expiresAt)
                && (origin == null || origin.equals(requestOrigin));
    }
    public void consume(Instant now) { consumedAt = now; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
}
