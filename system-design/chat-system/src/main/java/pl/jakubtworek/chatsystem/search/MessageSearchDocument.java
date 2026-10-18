package pl.jakubtworek.chatsystem.search;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.message.Message;
import java.time.Instant;
import java.util.UUID;

/** Odbudowywalna projekcja: nie jest źródłem prawdy i nie przechowuje ACL. */
@Entity
@Table(name = "message_search_documents", indexes = {
        @Index(name = "idx_search_conversation_sequence", columnList = "conversation_id, sequence_number")})
public class MessageSearchDocument {
    @Id @Column(name = "message_id") private UUID messageId;
    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(name = "sender_id", nullable = false) private UUID senderId;
    @Column(name = "sequence_number", nullable = false) private long sequenceNumber;
    @Column(length = 4000) private String body;
    @Column(nullable = false) private int revision;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected MessageSearchDocument() {}
    public MessageSearchDocument(Message message) { updateFrom(message); }
    public void updateFrom(Message message) {
        messageId = message.getId(); conversationId = message.getConversation().getId();
        senderId = message.getSender().getId(); sequenceNumber = message.getSequenceNumber();
        body = message.getDeletedAt() == null ? message.getBody() : null;
        revision = message.getRevision(); updatedAt = Instant.now();
    }
    public UUID getMessageId() { return messageId; }
}
