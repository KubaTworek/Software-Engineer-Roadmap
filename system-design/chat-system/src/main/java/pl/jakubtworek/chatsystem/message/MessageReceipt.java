package pl.jakubtworek.chatsystem.message;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.conversation.Conversation;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_receipts",
        indexes = {
                @Index(name = "idx_receipts_user_conversation", columnList = "recipient_id, conversation_id"),
                @Index(name = "idx_receipts_message", columnList = "message_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_message_recipient",
                columnNames = {"message_id", "recipient_id"}
        ))
public class MessageReceipt {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected MessageReceipt() {}

    public MessageReceipt(Message message, Conversation conversation, AppUser recipient) {
        this.message = message;
        this.conversation = conversation;
        this.recipient = recipient;
    }

    public void markDelivered(Instant at) {
        if (deliveredAt == null) {
            deliveredAt = at;
        }
    }

    public void markRead(Instant at) {
        markDelivered(at);
        if (readAt == null) {
            readAt = at;
        }
    }

    public MessageStatus status() {
        if (readAt != null) {
            return MessageStatus.READ;
        }
        if (deliveredAt != null) {
            return MessageStatus.DELIVERED;
        }
        return MessageStatus.SENT;
    }

    public UUID getId() { return id; }
    public Message getMessage() { return message; }
    public Conversation getConversation() { return conversation; }
    public AppUser getRecipient() { return recipient; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
}
