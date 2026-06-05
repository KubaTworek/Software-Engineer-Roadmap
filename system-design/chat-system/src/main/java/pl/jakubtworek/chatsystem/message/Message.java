package pl.jakubtworek.chatsystem.message;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.conversation.Conversation;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages",
        indexes = {
                @Index(name = "idx_messages_conversation_created", columnList = "conversation_id, created_at DESC"),
                @Index(name = "idx_messages_sender_client", columnList = "sender_id, client_message_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sender_client_message",
                columnNames = {"sender_id", "client_message_id"}
        ))
public class Message {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private AppUser sender;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {}

    public Message(Conversation conversation, AppUser sender, UUID clientMessageId, String body) {
        this.conversation = conversation;
        this.sender = sender;
        this.clientMessageId = clientMessageId;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public AppUser getSender() { return sender; }
    public UUID getClientMessageId() { return clientMessageId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
