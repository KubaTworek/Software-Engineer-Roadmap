package pl.jakubtworek.chatsystem.presence;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_presence")
public class UserPresence {
    @Id
    private UUID id = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PresenceStatus status = PresenceStatus.OFFLINE;

    private Instant lastSeenAt;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserPresence() {}

    public UserPresence(AppUser user) {
        this.user = user;
        this.status = PresenceStatus.OFFLINE;
        this.lastSeenAt = Instant.now();
    }

    public void markOnline(Instant now) {
        status = PresenceStatus.ONLINE;
        updatedAt = now;
    }

    public void markOffline(Instant now) {
        status = PresenceStatus.OFFLINE;
        lastSeenAt = now;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public PresenceStatus getStatus() { return status; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
