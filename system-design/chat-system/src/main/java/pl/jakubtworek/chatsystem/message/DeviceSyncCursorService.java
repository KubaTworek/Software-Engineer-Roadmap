package pl.jakubtworek.chatsystem.message;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class DeviceSyncCursorService {
    private final DeviceSyncCursorRepository cursors;
    public DeviceSyncCursorService(DeviceSyncCursorRepository cursors) { this.cursors = cursors; }

    /** Klient deklaruje ostatni trwale zapisany sequence; cursor nigdy nie cofa się przy retry. */
    @Transactional
    public void acknowledge(UUID userId, String deviceId, UUID conversationId, long sequence) {
        DeviceSyncCursor cursor = cursors.findByUserIdAndDeviceIdAndConversationId(userId, deviceId, conversationId)
                .orElseGet(() -> new DeviceSyncCursor(userId, deviceId, conversationId, sequence));
        cursor.advance(sequence);
        cursors.save(cursor);
    }
}
