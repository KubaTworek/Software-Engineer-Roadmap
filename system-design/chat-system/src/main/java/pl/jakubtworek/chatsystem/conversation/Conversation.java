package pl.jakubtworek.chatsystem.conversation;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.message.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {
    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_message_id")
    private Message lastMessage;

    private Instant lastMessageAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConversationMember> members = new ArrayList<>();

    protected Conversation() {}

    public Conversation(ConversationType type) {
        this.type = type;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addMember(ConversationMember member) {
        members.add(member);
    }

    public void updateLastMessage(Message message) {
        this.lastMessage = message;
        this.lastMessageAt = message.getCreatedAt();
    }

    public UUID getId() { return id; }
    public ConversationType getType() { return type; }
    public Message getLastMessage() { return lastMessage; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ConversationMember> getMembers() { return members; }
}
