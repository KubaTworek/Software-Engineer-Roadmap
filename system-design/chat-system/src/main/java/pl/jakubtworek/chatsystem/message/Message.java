package pl.jakubtworek.chatsystem.message;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.conversation.Conversation;
import pl.jakubtworek.chatsystem.media.Attachment;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "messages",
        indexes = {
                @Index(name = "idx_messages_conversation_sequence", columnList = "conversation_id, sequence_number DESC"),
                @Index(name = "idx_messages_sender_client", columnList = "sender_id, client_message_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_conversation_sender_client_message",
                        columnNames = {"conversation_id", "sender_id", "client_message_id"}),
                @UniqueConstraint(name = "uk_conversation_sequence",
                        columnNames = {"conversation_id", "sequence_number"})
        })
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

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;

    @Column(name = "command_fingerprint", nullable = false, updatable = false, length = 64)
    private String commandFingerprint;

    @Column(length = 4000)
    private String body;

    @ManyToMany
    @JoinTable(
            name = "message_attachments",
            joinColumns = @JoinColumn(name = "message_id"),
            inverseJoinColumns = @JoinColumn(name = "attachment_id")
    )
    private List<Attachment> attachments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MessageStatus status = MessageStatus.SENT;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant editedAt;
    private Instant deletedAt;

    @Column(nullable = false)
    private int revision = 1;

    protected Message() {}

    public Message(Conversation conversation, AppUser sender, UUID clientMessageId, long sequenceNumber,
                   String commandFingerprint, String body, List<Attachment> attachments) {
        this.conversation = conversation;
        this.sender = sender;
        this.clientMessageId = clientMessageId;
        this.sequenceNumber = sequenceNumber;
        this.commandFingerprint = commandFingerprint;
        this.body = body;
        if (attachments != null) {
            this.attachments.addAll(attachments);
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public AppUser getSender() { return sender; }
    public UUID getClientMessageId() { return clientMessageId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public String getCommandFingerprint() { return commandFingerprint; }
    public String getBody() { return body; }
    public List<Attachment> getAttachments() { return attachments; }
    public MessageStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public int getRevision() { return revision; }

    /** Edycja zmienia treść, ale nigdy pozycję wiadomości w historii. */
    public void edit(String newBody) {
        if (deletedAt != null) throw new IllegalStateException("Deleted message cannot be edited");
        body = newBody;
        revision++;
        editedAt = Instant.now();
    }

    /** Tombstone zachowuje sequence i pozwala klientom zsynchronizować usunięcie. */
    public void delete() {
        if (deletedAt == null) {
            body = null;
            attachments.clear();
            revision++;
            deletedAt = Instant.now();
        }
    }
}
