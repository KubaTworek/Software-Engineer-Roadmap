package pl.jakubtworek.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.jakubtworek.booking.entity.OutboundMessage;

import java.util.List;
import java.util.UUID;

public interface OutboundMessageRepository extends JpaRepository<OutboundMessage, UUID> {
    List<OutboundMessage> findByReservationId(UUID reservationId);
}
