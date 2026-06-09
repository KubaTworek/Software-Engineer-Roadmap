package pl.jakubtworek.chatsystem.presence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserPresenceRepository extends JpaRepository<UserPresence, UUID> {
    Optional<UserPresence> findByUserId(UUID userId);
}
