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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationRole role = ConversationRole.MEMBER;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    protected ConversationMember() {}

    public ConversationMember(Conversation conversation, AppUser user) {
        this(conversation, user, ConversationRole.MEMBER);
    }

    public ConversationMember(Conversation conversation, AppUser user, ConversationRole role) {
        this.conversation = conversation;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        joinedAt = Instant.now();
    }

    public void markRead(Instant at) {
        if (lastReadAt == null || at.isAfter(lastReadAt)) {
            lastReadAt = at;
        }
    }

    public void changeRole(ConversationRole role) {
        this.role = role;
    }

    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public AppUser getUser() { return user; }
    public ConversationRole getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLastReadAt() { return lastReadAt; }
}
