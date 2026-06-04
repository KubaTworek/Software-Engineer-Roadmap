package pl.jakubtworek.backend.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.jakubtworek.backend.catalog.domain.EventEntity;

import java.util.UUID;

public interface EventRepository extends JpaRepository<EventEntity, UUID> {
}
