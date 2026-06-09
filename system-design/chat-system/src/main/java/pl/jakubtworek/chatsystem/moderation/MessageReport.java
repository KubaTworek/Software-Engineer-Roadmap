package pl.jakubtworek.chatsystem.moderation;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.message.Message;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_reports", indexes = @Index(name = "idx_message_reports_status", columnList = "status"))
public class MessageReport {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private AppUser reporter;

    @Column(nullable = false, length = 120)
    private String reason;

    @Column(length = 2000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModerationStatus status = ModerationStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected MessageReport() {}

    public MessageReport(Message message, AppUser reporter, String reason, String details) {
        this.message = message;
        this.reporter = reporter;
        this.reason = reason;
        this.details = details;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public Message getMessage() { return message; }
    public AppUser getReporter() { return reporter; }
    public String getReason() { return reason; }
    public String getDetails() { return details; }
    public ModerationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
