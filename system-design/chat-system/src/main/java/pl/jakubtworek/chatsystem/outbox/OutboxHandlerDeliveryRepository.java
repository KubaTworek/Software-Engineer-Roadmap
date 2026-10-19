package pl.jakubtworek.chatsystem.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OutboxHandlerDeliveryRepository extends JpaRepository<OutboxHandlerDelivery, UUID> {
    boolean existsByEventIdAndHandlerName(UUID eventId, String handlerName);
}
