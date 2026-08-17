package pl.jakubtworek.chatsystem.blocking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, UUID> {
    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    boolean existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(UUID blockerA, UUID blockedA, UUID blockerB, UUID blockedB);
    Optional<BlockedUser> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    List<BlockedUser> findByBlockerId(UUID blockerId);
}
