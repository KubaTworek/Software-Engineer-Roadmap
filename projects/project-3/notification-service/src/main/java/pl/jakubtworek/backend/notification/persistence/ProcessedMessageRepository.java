package pl.jakubtworek.backend.notification.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {
    /**
     * Jedna instrukcja usuwa wyścig exists→insert pomiędzy replikami konsumenta.
     * Wstawienie należy do transakcji listenera, więc błąd efektu wycofa również marker.
     */
    @Modifying
    @Query(value = "INSERT INTO processed_messages(message_id, processed_at) VALUES (:id, :at) " +
            "ON CONFLICT (message_id) DO NOTHING", nativeQuery = true)
    int tryMarkProcessed(@Param("id") UUID id, @Param("at") Instant at);
}
