package pl.jakubtworek.chatsystem.message;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSyncCursorRepository extends JpaRepository<DeviceSyncCursor, UUID> {
    Optional<DeviceSyncCursor> findByUserIdAndDeviceIdAndConversationId(UUID userId, String deviceId, UUID conversationId);
}
