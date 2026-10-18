package pl.jakubtworek.chatsystem.conversation;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversation_member_epoch",
                columnNames = {"conversation_id", "user_id", "membership_epoch"}
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

    /**
     * Zakres widoczności historii jest częścią członkostwa, a nie właściwością użytkownika.
     * Ponowne dołączenie tworzy nową epokę i nie przywraca automatycznie dostępu do starej historii.
     */
    @Column(name = "joined_sequence", nullable = false, updatable = false)
    private long joinedSequence;

    @Column(name = "left_sequence")
    private Long leftSequence;

    @Column(name = "membership_epoch", nullable = false, updatable = false)
    private int membershipEpoch;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    protected ConversationMember() {}

    public ConversationMember(Conversation conversation, AppUser user) {
        this(conversation, user, ConversationRole.MEMBER, 1, 1);
    }

    public ConversationMember(Conversation conversation, AppUser user, ConversationRole role) {
        this(conversation, user, role, 1, 1);
    }

    public ConversationMember(Conversation conversation, AppUser user, ConversationRole role,
                              long joinedSequence, int membershipEpoch) {
        this.conversation = conversation;
        this.user = user;
        this.role = role;
        this.joinedSequence = joinedSequence;
        this.membershipEpoch = membershipEpoch;
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

    /** leftSequence jest inkluzywne: wiadomości zaakceptowane później nie należą do tej epoki. */
    public void leave(long lastVisibleSequence, Instant at) {
        if (leftAt == null) {
            leftSequence = lastVisibleSequence;
            leftAt = at;
        }
    }

    public boolean isActive() { return leftAt == null; }

    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public AppUser getUser() { return user; }
    public ConversationRole getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLastReadAt() { return lastReadAt; }
    public long getJoinedSequence() { return joinedSequence; }
    public Long getLeftSequence() { return leftSequence; }
    public int getMembershipEpoch() { return membershipEpoch; }
    public Instant getLeftAt() { return leftAt; }
}
