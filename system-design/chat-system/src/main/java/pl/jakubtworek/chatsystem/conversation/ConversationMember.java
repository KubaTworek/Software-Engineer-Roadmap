package pl.jakubtworek.chatsystem.conversation;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversation_member",
                columnNames = {"conversation_id", "user_id"}
        ))
public class ConversationMember {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    protected ConversationMember() {}

    public ConversationMember(Conversation conversation, AppUser user) {
        this.conversation = conversation;
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        joinedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public AppUser getUser() { return user; }
    public Instant getJoinedAt() { return joinedAt; }
}
