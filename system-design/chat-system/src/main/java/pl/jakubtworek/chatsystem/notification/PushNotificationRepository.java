package pl.jakubtworek.chatsystem.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PushNotificationRepository extends JpaRepository<PushNotification, UUID> {
    List<PushNotification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
}
