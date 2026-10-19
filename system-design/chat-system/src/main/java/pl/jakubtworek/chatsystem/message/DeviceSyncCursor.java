package pl.jakubtworek.chatsystem.message;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Osobny cursor każdego urządzenia; read receipt pozostaje stanem użytkownika. */
@Entity
@Table(name = "device_sync_cursors", uniqueConstraints = @UniqueConstraint(
        name = "uk_device_conversation_cursor", columnNames = {"user_id", "device_id", "conversation_id"}))
public class DeviceSyncCursor {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "device_id", nullable = false, length = 120) private String deviceId;
    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(name = "last_sequence", nullable = false) private long lastSequence;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected DeviceSyncCursor() {}
    public DeviceSyncCursor(UUID userId, String deviceId, UUID conversationId, long sequence) {
        this.userId=userId; this.deviceId=deviceId; this.conversationId=conversationId; advance(sequence);
    }
    public void advance(long sequence) { if (sequence > lastSequence) lastSequence=sequence; updatedAt=Instant.now(); }
    public long getLastSequence() { return lastSequence; }
}
