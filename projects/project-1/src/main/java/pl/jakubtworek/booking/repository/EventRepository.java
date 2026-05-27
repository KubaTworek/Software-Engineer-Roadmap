package pl.jakubtworek.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.jakubtworek.booking.entity.Event;

import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
}
