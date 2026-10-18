package pl.jakubtworek.chatsystem.conversation;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.message.Message;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(
        name = "uk_conversations_direct_key", columnNames = "direct_key"))
public class Conversation {
    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationType type;

    @Column(length = 120)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_message_id")
    private Message lastMessage;

    private Instant lastMessageAt;

    /**
     * Kanoniczny, niezależny od kolejności klucz pary użytkowników rozmowy DIRECT.
     * Constraint w bazie jest ostateczną ochroną przed równoległym utworzeniem A→B i B→A.
     */
    @Column(name = "direct_key", length = 73)
    private String directKey;

    /** Następny numer porządkowy jest przydzielany pod blokadą wiersza konwersacji. */
    @Column(name = "next_sequence", nullable = false)
    private long nextSequence = 1;

    @Version
    private long version;

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

    public Conversation(ConversationType type, String directKey) {
        this.type = type;
        this.directKey = directKey;
    }

    public Conversation(ConversationType type, String title, AppUser createdBy) {
        this.type = type;
        this.title = title;
        this.createdBy = createdBy;
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

    public long allocateSequence() {
        return nextSequence++;
    }

    public void updateLastMessage(Message message) {
        if (lastMessage == null || message.getSequenceNumber() > lastMessage.getSequenceNumber()) {
            this.lastMessage = message;
            this.lastMessageAt = message.getCreatedAt();
        }
    }

    public void rename(String title) {
        this.title = title;
    }

    public UUID getId() { return id; }
    public ConversationType getType() { return type; }
    public String getTitle() { return title; }
    public AppUser getCreatedBy() { return createdBy; }
    public Message getLastMessage() { return lastMessage; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public String getDirectKey() { return directKey; }
    public long getNextSequence() { return nextSequence; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ConversationMember> getMembers() { return members; }
}
