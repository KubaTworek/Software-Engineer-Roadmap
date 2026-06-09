package pl.jakubtworek.chatsystem.blocking;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blocked_users", uniqueConstraints = @UniqueConstraint(name = "uk_block_pair", columnNames = {"blocker_id", "blocked_id"}))
public class BlockedUser {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private AppUser blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private AppUser blocked;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BlockedUser() {}

    public BlockedUser(AppUser blocker, AppUser blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public AppUser getBlocker() { return blocker; }
    public AppUser getBlocked() { return blocked; }
    public Instant getCreatedAt() { return createdAt; }
}
