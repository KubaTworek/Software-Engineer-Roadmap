package pl.jakubtworek.chatsystem.notification;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.conversation.Conversation;
import pl.jakubtworek.chatsystem.message.Message;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_notifications")
public class PushNotification {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushNotificationStatus status = PushNotificationStatus.CREATED;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant sentAt;

    protected PushNotification() {}

    public PushNotification(AppUser recipient, Conversation conversation, Message message, String title, String body) {
        this.recipient = recipient;
        this.conversation = conversation;
        this.message = message;
        this.title = title;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markSent() {
        status = PushNotificationStatus.SENT;
        sentAt = Instant.now();
    }

    public void markFailed() {
        status = PushNotificationStatus.FAILED;
    }

    public UUID getId() { return id; }
    public AppUser getRecipient() { return recipient; }
    public Conversation getConversation() { return conversation; }
    public Message getMessage() { return message; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public PushNotificationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
